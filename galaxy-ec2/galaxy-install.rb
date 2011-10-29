#!/usr/bin/env ruby

require 'open-uri'
require 'rexml/document'
require 'fileutils'
require 'net/http'
require 'uri'
require 'etc'

class MavenRepo
  def initialize(repo_urls)
    @repo_urls = repo_urls
  end

  def resolve(group_id, artifact_id, version, classifier)
    result = nil
    errors = []
    @repo_urls.each do |url|
      if version =~ /-SNAPSHOT$/
        uri = url + path_for(group_id, artifact_id, version, 'maven-metadata.xml')
        begin
          metadata = open(uri) { |io| io.read }

          document = REXML::Document.new(metadata)

          timestamp = REXML::XPath.first(document, '/metadata/versioning/snapshot/timestamp').text
          build_number = REXML::XPath.first(document, '/metadata/versioning/snapshot/buildNumber').text

          file_name = "#{artifact_id}-#{version.gsub(/-SNAPSHOT$/, '')}-#{timestamp}-#{build_number}.#{classifier}"
        rescue => e
          errors << [uri, e]
          next
        end
      else
        file_name = "#{artifact_id}-#{version}.#{classifier}"
      end

      uri = URI.parse(url + path_for(group_id, artifact_id, version, file_name))
      request = Net::HTTP.new(uri.host, uri.port)

      begin
        next unless request.request_head(uri.path).code =~ /2../
        result = url + path_for(group_id, artifact_id, version, file_name)
        break
      rescue => e
        errors << [uri.to_s, e]
        next
      end
    end

    # TODO: if errors raise exception

    return result, errors
  end

  def path_for(group_id, artifact_id, version, file_name)
    ([''] + group_id.split('.') + [artifact_id, version, file_name]).join('/')
  end
end


def download(url, dest_file)
  open(dest_file, "wb") do |output|
    open(url) do |input|
      IO.copy_stream(input, output)
    end
  end
end

def untar(file, dest_path)
  `tar xzf #{file} -C #{dest_path}`
end

def parse(properties_file)
  Hash[IO.readlines(properties_file).map { |line| line.strip.split(/\s*=\s*/, 2) }]
end


def write_properties(file, properties)
  File.open(file, "w") do |f|
    f.puts(properties.map { |k, v| "#{k}=#{v}" }.join("\n"))
  end
end



class AgentConfigurator
  def configure(deploy_path, properties)

    properties.merge!(
        'agent.slots-dir' => 'slots',
        'discovery.uri' => 'http://localhost:8411'
    )

    FileUtils.makedirs("#{deploy_path}/etc")
    write_properties("#{deploy_path}/etc/config.properties", properties)
    File.open("#{deploy_path}/etc/jvm.config", "w") do |file|
    end
  end
end

class CoordinatorConfigurator
  def configure(deploy_path, properties)

    properties.merge!(
        'coordinator.config-repo' => 'http://localhost:64001/v1/config',
        'http-server.http.port' => 64000
    )

    FileUtils.makedirs("#{deploy_path}/etc")
    write_properties("#{deploy_path}/etc/config.properties", properties)
    File.open("#{deploy_path}/etc/jvm.config", "w") do |file|
    end
  end
end

class ConfigurationServerConfigurator
  def configure(deploy_path, properties)

    properties.merge!(
            'configuration-repository.coordinator-uri' => 'http://localhost:64000',
            'http-server.http.port' => 64001
    )

    FileUtils.makedirs("#{deploy_path}/etc")
    write_properties("#{deploy_path}/etc/config.properties", properties)
    File.open("#{deploy_path}/etc/jvm.config", "w") do |file|
    end
  end
end

# TODO: use binary repo uris provided in config

urls = ['http://oss.sonatype.org/content/repositories/releases',
        'http://oss.sonatype.org/content/repositories/snapshots']

bashrc_path = '/home/ubuntu/.bashrc'
downloads_path = "/tmp/downloads"
install_path = "/mnt/galaxy"
config_dir = '/home/ubuntu/cloudconf'

CONFIGURATORS = {
        'galaxy-coordinator' => CoordinatorConfigurator.new,
        'galaxy-agent' => AgentConfigurator.new,
        'galaxy-configuration-repository' => ConfigurationServerConfigurator.new
}

# extract node metadata
availability_zone = open("http://169.254.169.254/latest/meta-data/placement/availability-zone") { |io| io.read }
instance_id = open("http://169.254.169.254/latest/meta-data/instance-id") { |io| io.read }

# ec2 availability zones use the name of the region plus a 1 letter zone identifier
region = availability_zone[0..-2]

location = "/ec2/#{region}/#{availability_zone}/#{instance_id}"


FileUtils.makedirs(install_path)

# become user 'ubuntu'
user = Etc.getpwnam('ubuntu')

FileUtils.chown(user.uid, user.gid, install_path)

Process::Sys.setgid(user.gid)
Process::Sys.setuid(user.uid)

raise "Can't change current user to 'ubuntu" unless Process.uid == user.uid && Process.gid == user.gid

ENV['HOME'] = user.dir

# need to do this after ENV['HOME'] is set
require 'rubygems/dependency_installer.rb'


manifest_file = "#{install_path}/manifest"

if File.exists?(manifest_file)
  # TODO: print more diagnostics info?
  puts "Already installed. Nothing to do"
  exit
end

FileUtils.makedirs(downloads_path)


install_manifest = parse(config_dir + '/installer.properties')


# TODO: coordinator_url = ....
coordinator_url="http://localhost:64000"

group_id = 'com.proofpoint.galaxy'
version = install_manifest['galaxy.version']

# install galaxy gem
gem_url, errors = MavenRepo.new(urls).resolve(group_id, 'galaxy-cli', version, "gem")
if gem_url.nil?
  errors.each { |uri, e| puts "#{uri} => #{e.message}" }
  raise "Could not resolve url for #{group_id}:galaxy:#{version}:gem"
end

gem_binary_path = "#{downloads_path}/galaxy-#{version}.gem"
download(gem_url, gem_binary_path)

gemspec = Gem::DependencyInstaller.new(:user_install => true).install(gem_binary_path).last

# generate bashrc include
File.open("#{install_path}/bashrc", "w") do |file|
  galaxy_path = File.dirname(Gem.bin_path(gemspec.name, 'galaxy'))
  file.puts <<-FILE
    export PATH=$PATH:#{galaxy_path}
    export GALAXY_COORDINATOR=#{coordinator_url}

    YELLOW="\\[\\033[33m\\]"
    DEFAULT="\\[\\033[39m\\]"
    export PS1="[$(date +%H:%M) \\u@${YELLOW}#{instance_id}${DEFAULT}:\\w] "
  FILE
end

# TODO: verify that bashrc doesn't already contain this line
File.open(bashrc_path, "a") do |file|
  file.puts("source #{install_path}/bashrc")
end



# TODO: this is what microgalaxy should do for us...
install_manifest['artifacts'].split(',').each do |artifact_id|

  binary_path = "#{downloads_path}/#{artifact_id}-#{version}.tgz"

  binary_url, errors = MavenRepo.new(urls).resolve(group_id, artifact_id, version, "tar.gz")

  if binary_url.nil?
    errors.each { |uri, e| puts "#{uri} => #{e.message}" }
    raise "Could not resolve url for #{group_id}:#{artifact_id}:#{version}:tar.gz"
  end

  puts "Downloading #{binary_url} to #{binary_path}"
  download(binary_url, binary_path)

  puts "Unpacking #{binary_path} into #{install_path}"
  untar(binary_path, install_path)

  deploy_path = install_path + "/" + artifact_id + "-" + version
  puts "Configuring #{deploy_path}"

  data_dir = "#{install_path}/#{artifact_id}-data"
  puts "Creating data directory '#{data_dir}'"
  FileUtils.makedirs(data_dir)


  node_properties = {
    'node.location' => location,
    'node.data-dir' => data_dir,
    'node.environment' => install_manifest['environment']
  }

  FileUtils.makedirs("#{deploy_path}/etc")
  write_properties("#{deploy_path}/etc/node.properties", node_properties)

  CONFIGURATORS[artifact_id].configure(deploy_path, parse("#{config_dir}/#{artifact_id}.properties"))

  if artifact_id == 'galaxy-agent' && File.exists?("#{config_dir}/galaxy-agent-resources.properties")
    FileUtils.copy("#{config_dir}/galaxy-agent-resources.properties", "#{deploy_path}/etc/resources.properties")
  end

  puts "Launching #{deploy_path}/bin/launcher"
  system("#{deploy_path}/bin/launcher start")

  File.delete(install_path + "/#{artifact_id}") rescue nil
  File.symlink(install_path + "/#{artifact_id}-#{version}", install_path + "/#{artifact_id}")
end


# create installation completed marker
FileUtils.touch(manifest_file)

# TODO: verify md5/sha1 signature


