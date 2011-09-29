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

class AgentConfigurator
  def configure(deploy_path, properties)
    FileUtils.makedirs("#{deploy_path}/etc")

    File.open("#{deploy_path}/etc/config.properties", "w") do |file|
      file.puts <<-PROPERTIES
        node.environment=#{properties['node.environment']}
        agent.coordinator-uri=#{properties['agent.coordinator-uri']}
        http-server.http.port=0
        agent.slots-dir=/tmp/agent/slots
        discovery.uri=http://localhost:8080
      PROPERTIES
    end

    File.open("#{deploy_path}/etc/jvm.config", "w") do |file|
    end
  end
end

class CoordinatorConfigurator
  def configure(deploy_path, properties)
    FileUtils.makedirs("#{deploy_path}/etc")

    File.open("#{deploy_path}/etc/config.properties", "w") do |file|
      file.puts <<-PROPERTIES
        node.environment=#{properties['node.environment']}
        coordinator.binary-repo=#{properties['coordinator.binary-repo']}
        coordinator.config-repo=http://localhost:64001/v1/config
        http-server.http.port=64000
      PROPERTIES
    end

    File.open("#{deploy_path}/etc/jvm.config", "w") do |file|
    end
  end
end

class ConfigurationServerConfigurator
  def configure(deploy_path, properties)
    FileUtils.makedirs("#{deploy_path}/etc")

    File.open("#{deploy_path}/etc/config.properties", "w") do |file|
      file.puts <<-PROPERTIES
        node.environment=#{properties['node.environment']}
        configuration-repository.git.uri=#{properties['configuration-repository.git.uri']}
        configuration-repository.coordinator-uri=http://localhost:64000
        http-server.http.port=64001
      PROPERTIES
    end

    File.open("#{deploy_path}/etc/jvm.config", "w") do |file|
    end
  end
end

# TODO: use binary repo uris provided in config

urls = ['http://oss.sonatype.org/content/repositories/releases',
        'http://oss.sonatype.org/content/repositories/snapshots']

#config_dir = '/tmp/cloudconf'
#install_path = "/tmp/galaxy"
#downloads_path = '/tmp/downloads'

bashrc_path = '/home/ubuntu/.bashrc'
downloads_path = "/home/ubuntu/downloads"
install_path = "/home/ubuntu/galaxy"
config_dir = '/home/ubuntu/cloudconf'

configurators = {
        'galaxy-coordinator' => CoordinatorConfigurator.new,
        'galaxy-agent' => AgentConfigurator.new,
        'galaxy-configuration-repository' => ConfigurationServerConfigurator.new
}


# become user 'ubuntu'
user = Etc.getpwnam('ubuntu')
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

FileUtils.makedirs(install_path)
FileUtils.makedirs(downloads_path)

# TODO: move galaxy installation properties to separate file
  # galaxy version
  # galaxy install location
  # downloads dir

group_id = "com.proofpoint.galaxy"
version = nil
coordinator_url = nil # TODO: grab from config

# Download tarballs
Dir[config_dir + '/*.properties'].each do |config_file|
  artifact_id = File.basename(config_file, '.properties')

  puts "Found #{artifact_id}"
  properties = parse(config_file)

  version = properties['galaxy.version']
  coordinator_url = properties['agent.coordinator-uri']

  raise "Missing galaxy.version in #{config_file}" if version.nil?
  puts "Version is #{version}"

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

  configurators[artifact_id].configure(deploy_path, properties)

  puts "Launching #{deploy_path}/bin/launcher"
  system("#{deploy_path}/bin/launcher start")

  File.delete(install_path + "/#{artifact_id}") rescue nil
  File.symlink(install_path + "/#{artifact_id}-#{version}", install_path + "/#{artifact_id}")
end

# download galaxy gem
unless version.nil? # picks last version seen above
  artifact_id = 'galaxy'
  gem_url, errors = MavenRepo.new(urls).resolve(group_id, artifact_id, version, "gem")
  if gem_url.nil?
      errors.each { |uri, e| puts "#{uri} => #{e.message}" }
      raise "Could not resolve url for #{group_id}:#{artifact_id}:#{version}:gem"
    end

  gem_binary_path = "#{downloads_path}/#{artifact_id}-#{version}.gem"
  download(gem_url, gem_binary_path)

  gemspec = Gem::DependencyInstaller.new(:user_install => true).install(gem_binary_path).last

  # generate bashrc include
  File.open("#{install_path}/galaxy-bashrc", "w") do |file|
    galaxy_path = File.dirname(Gem.bin_path(gemspec.name, 'galaxy'))
    file.puts <<-FILE
      export PATH=$PATH:#{galaxy_path}
      export GALAXY_COORDINATOR=#{coordinator_url}

      YELLOW="\\[\\033[33m\\]"
      DEFAULT="\\[\\033[39m\\]"
      export PS1="[$(date +%H:%M) \\u@$YELLOW\\h$DEFAULT:\\w] "
    FILE
  end

  # TODO: verify that bashrc doesn't already contain this line
  File.open(bashrc_path, "a") do |file|
    file.puts("source #{install_path}/galaxy-bashrc")
  end

end

# create installation completed marker
FileUtils.touch(manifest_file)

# TODO: verify md5/sha1 signature
