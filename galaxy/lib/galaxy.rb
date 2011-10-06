#!/usr/bin/env ruby
require 'optparse'
require 'httpclient'
require 'json'
require 'galaxy/version'
require 'galaxy/colorize'
require 'galaxy/shell'

module Galaxy
  GALAXY_VERSION = "0.1"

  EXIT_CODES = {
      :success => 0,
      :no_slots => 1,
      :unsupported => 3,
      :invalid_usage => 64,
      :general_error => 99
  }

  #
  # Slot Information
  #
  class Slot
    attr_reader :uuid, :short_id, :host, :ip, :url, :binary, :config, :status, :status_message, :path

    def initialize(uuid, short_id, url, binary, config, status, status_message, path)
      @uuid = uuid
      @short_id = short_id
      @url = url
      @binary = binary
      @config = config
      @status = status
      @status_message = status_message
      @path = path
      uri = URI.parse(url)
      @host = uri.host
      @ip = IPSocket::getaddress(host)
    end

    def columns(colors = false)
      status = @status
      status_message = @status_message

      if colors
        status = case status
          when "RUNNING" then Colorize::colorize(status, :bright, :green)
          when "STOPPED" then status
          else status
        end
        status_message = Colorize::colorize(status_message, :red)
      end

      return [@short_id, @host, status, @binary, @config, status_message].map { |value| value || '' }
    end
  end
  #
  # Agent Information
  #
  class Agent
    attr_reader :agent_id, :host, :ip, :url, :status, :location, :path, :instance_type

    def initialize(agent_id, status, url, location, instance_type)
      @agent_id = agent_id
      @url = url
      @status = status
      @path = path
      uri = URI.parse(url)
      @host = uri.host
      @ip = IPSocket::getaddress(host)
      @location = location
      @instance_type = instance_type
    end

    def columns(colors = false)
      status = @status

      if colors
        status = case status
          when "ONLINE" then Colorize::colorize(status, :bright, :green)
          when "OFFLINE" then Colorize::colorize(status, :bright, :red)
          when "PROVISIONING" then Colorize::colorize(status, :bright, :blue)
          else status
        end
      end

      return [@agent_id, @host, status, @instance_type, @location].map { |value| value || '' }
    end
  end

  class CommandError < RuntimeError
    attr_reader :code
    attr_reader :message

    def initialize(code, message)
      @code = code
      @message = message
    end
  end

  class Commands
    def self.show(filter, options, args)
      if !args.empty? then
        raise CommandError.new(:invalid_usage, "You can not pass arguments to show.")
      end
      coordinator_request(filter, options, :get)
    end

    def self.install(filter, options, args)
      if args.size != 2 then
        raise CommandError.new(:invalid_usage, "You must specify a binary and config to install.")
      end
      if args[0].start_with? '@'
        config = args[0]
        binary = args[1]
      else
        binary = args[0]
        config = args[1]

      end
      installation = {
          :binary => binary,
          :config => config
      }
      coordinator_request(filter, options, :post, nil, installation, true)
    end

    def self.upgrade(filter, options, args)
      if args.size <= 0 || args.size > 2 then
        raise CommandError.new(:invalid_usage, "You must specify a binary version or a config version for upgrade.")
      end
      if filter.empty? then
        raise CommandError.new(:invalid_usage, "You must specify a filter when for upgrade.")
      end

      if args[0].start_with? '@'
        config_version = args[0][1..-1]
        binary_version = args[1] if args.size > 1
      else
        binary_version = args[0]
        config_version = args[1][1..-1] if args.size > 1

      end
      versions = {}
      versions[:binaryVersion] = binary_version if binary_version
      versions[:configVersion] = config_version if config_version

      coordinator_request(filter, options, :post, 'assignment', versions, true)
    end

    def self.terminate(filter, options, args)
      if !args.empty? then
        raise CommandError.new(:invalid_usage, "You can not pass arguments to terminate.")
      end
      if filter.empty? then
        raise CommandError.new(:invalid_usage, "You must specify a filter when for terminate.")
      end
      coordinator_request(filter, options, :delete)
    end

    def self.start(filter, options, args)
      if !args.empty? then
        raise CommandError.new(:invalid_usage, "You can not pass arguments to start.")
      end
      if filter.empty? then
        raise CommandError.new(:invalid_usage, "You must specify a filter when for start.")
      end
      coordinator_request(filter, options, :put, 'lifecycle', 'running')
    end

    def self.stop(filter, options, args)
      if !args.empty? then
        raise CommandError.new(:invalid_usage, "You can not pass arguments to stop.")
      end
      if filter.empty? then
        raise CommandError.new(:invalid_usage, "You must specify a filter when for stop.")
      end
      coordinator_request(filter, options, :put, 'lifecycle', 'stopped')
    end

    def self.restart(filter, options, args)
      if !args.empty? then
        raise CommandError.new(:invalid_usage, "You can not pass arguments to restart.")
      end
      if filter.empty? then
        raise CommandError.new(:invalid_usage, "You must specify a filter when for restart.")
      end
      coordinator_request(filter, options, :put, 'lifecycle', 'restarting')
    end

    def self.ssh(filter, options, args)
      if !args.empty? then
        raise CommandError.new(:invalid_usage, "You can not pass arguments to ssh.")
      end
      if filter.empty? then
        raise CommandError.new(:invalid_usage, "You must specify a filter when for ssh.")
      end
      slots = show(filter, options, args)
      if slots.empty?
        return []
      end

      slot = slots.first
      ssh = ENV['GALAXY_SSH_COMMAND'] || "ssh"

      if slot.path.nil?
        path = "$HOME"
      else
        path = Shell::quote(slot.path)
      end
      remote_command = "cd #{path}; #{options[:ssh_command]}"
      command = "#{ssh} #{slot.host} -t #{Shell::quote(remote_command)}"

      puts command if options[:debug]
      system(command)
      []
    end

    def self.agent_show(filter, options, args)
      if !args.empty? then
        raise CommandError.new(:invalid_usage, "You can not pass arguments to agent show.")
      end
      if !filter.empty? then
        raise CommandError.new(:invalid_usage, "You can not use filters with agent show.")
      end
      coordinator_agent_request(filter, options, :get)
    end

    def self.agent_add(filter, options, args)
      if args.size > 1 then
        raise CommandError.new(:invalid_usage, "Agent add only accepts one argument.")
      end
      if !filter.empty? then
        raise CommandError.new(:invalid_usage, "You can not use filters with agent show.")
      end

      if args.size > 0 then
        instance_type = args[0]
      end

      provisioning = {}
      provisioning[:instanceType] = instance_type if instance_type
      provisioning[:availabilityZone] = options[:availability_zone] if options[:availability_zone]

      query = "count=#{options[:count] || 1}"

      coordinator_agent_request(filter, options, :post, query, nil, provisioning, true)
    end

    private

    def self.coordinator_request(filter, options, method, sub_path = nil, value = nil, is_json = false)
      # build the uri
      uri = options[:coordinator_url]
      uri += '/' unless uri.end_with? '/'
      uri += 'v1/slot/'
      uri += sub_path unless sub_path.nil?

      # create filter query
      params = filter
      # todo this arbitrary rename here is just wrong
      params["limit"] <<= options[:count] unless options[:count].nil?
      params["pretty"] <<= "true" unless options[:debug].nil?
      query = params.map { |k, v| v.map { |v1| "#{k}=#{URI.escape(v1)}" }.join('&') }.join('&')

      # encode body as json if necessary
      body = value
      headers = {}
      if is_json
        body = value.to_json
        headers['Content-Type'] = 'application/json'
      end

      # log request in as a valid curl command if in debug mode
      if options[:debug]
        if value then
          puts "curl -H 'Content-Type: application/json' -X#{method.to_s.upcase} '#{uri}?#{query}' -d '"
          puts body
          puts "'"
        else
          puts "curl -X#{method.to_s.upcase} '#{uri}?#{query}'"
        end
      end

      # execute request
      response = HTTPClient.new.request(method, uri, query, body, headers)
      response_body = response.body if response.respond_to?(:body)
      response_body = response_body.content if response_body.respond_to?(:content)
      # log response if in debug mode
      if options[:debug]
        puts
        puts response
        puts response_body
      end

      # check response code
      if response.status / 100 != 2 then
        raise CommandError.new(:general_error, response.reason || "Unknown error executing command")
      end

      # parse response as json
      slots_json = JSON.parse(response_body)

      # convert parsed json into slot objects
      slots = slots_json.map do |slot_json|
        Slot.new(slot_json['id'], slot_json['shortId'], slot_json['self'], slot_json['binary'], slot_json['config'], slot_json['status'], slot_json['statusMessage'], slot_json['installPath'])
      end

      # verify response
      if slots.empty? then
        raise CommandError.new(:no_slots, "No slots match the provided filters.")
      end

      slots
    end

    def self.coordinator_agent_request(filter, options, method, query = '', sub_path = nil, value = nil, is_json = false)
      # build the uri
      uri = options[:coordinator_url]
      uri += '/' unless uri.end_with? '/'
      uri += 'v1/admin/agent'
      uri += sub_path unless sub_path.nil?

      # encode body as json if necessary
      body = value
      headers = {}
      if is_json
        body = value.to_json
        headers['Content-Type'] = 'application/json'
      end

      # log request in as a valid curl command if in debug mode
      if options[:debug]
        if value then
          puts "curl -H 'Content-Type: application/json' -X#{method.to_s.upcase} '#{uri}?#{query}' -d '"
          puts body
          puts "'"
        else
          puts "curl -X#{method.to_s.upcase} '#{uri}?#{query}'"
        end
      end

      # execute request
      response = HTTPClient.new.request(method, uri, query, body, headers)

      # check response code
      if response.status / 100 != 2 then
        if options[:debug]
          puts response
        end
        raise CommandError.new(:command_error, response.reason || "Unknown error executing command")
      end

      # parse response as json
      response = response.body if response.respond_to?(:body)
      response = response.content if response.respond_to?(:content)
      agents_json = JSON.parse(response)

      # log response if in debug mode
      if options[:debug]
        puts agents_json
      end

      # convert parsed json into slot objects
      agents = agents_json.map do |agent_json|
        Agent.new(agent_json['agentId'], agent_json['state'], agent_json['self'], agent_json['location'], agent_json['instanceType'])
      end

      return agents
    end
  end

  class CLI

    COMMANDS = [:show, :install, :upgrade, :terminate, :start, :stop, :restart, :ssh, :agent_show, :agent_add]
    INITIAL_OPTIONS = {
        :coordinator_url => ENV['GALAXY_COORDINATOR'] || 'http://localhost:64000',
        :ssh_command => "bash --login"
    }

    def self.parse_command_line(args)
      options = INITIAL_OPTIONS

      filter = Hash.new{[]}

      option_parser = OptionParser.new do |opts|
        opts.banner = "Usage: #{File.basename($0)} [options] <command>"

        opts.separator ''
        opts.separator 'Options:'

        opts.on('-h', '--help', 'Display this screen') do
          puts opts
          exit EXIT_CODES[:success]
        end

        opts.on("-v", "--version", "Display the Galaxy version number and exit") do
          puts "Galaxy version #{GALAXY_VERSION}"
          exit EXIT_CODES[:success]
        end

        opts.on("--coordinator COORDINATOR", "Galaxy coordinator host (overrides GALAXY_COORDINATOR)") do |v|
          options[:coordinator_url] = v
        end

        opts.on('--debug', 'Enable debug messages') do
          options[:debug] = true
        end

        opts.separator ''
        opts.separator 'Filters:'

        opts.on("-b", "--binary BINARY", "Select slots with a given binary") do |arg|
          filter[:binary] <<= arg
        end

        opts.on("-c", "--config CONFIG", "Select slots with given configuration") do |arg|
          filter[:config] <<= arg
        end

        opts.on("-i", "--host HOST", "Select slots on the given hostname") do |arg|
          filter[:host] <<= arg
        end

        opts.on("-I", "--ip IP", "Select slots at the given IP address") do |arg|
          filter[:ip] <<= arg
        end

        opts.on("-u", "--uuid SLOT_UUID", "Select slots with given slot uuid") do |arg|
          filter[:uuid] <<= arg
        end

        opts.on("--count count", "Number of instances to install or agents to provision") do |arg|
          options[:count] = arg
        end

        opts.on("--availability-zone AVAILABILITY_ZONE", "Availability zone to agent provisioning") do |arg|
          options[:availability_zone] = arg
        end

        # todo find a better command line argument
        opts.on("-x", "--ssh-command SSH_COMMAND", "Command to execute with ssh") do |arg|
          options[:ssh_command] = arg
        end

        opts.on("-s", "--state STATE", "Select 'r{unning}', 's{topped}' or 'unknown' slots", [:running, :r, :stopped, :s, :unknown]) do |arg|
          case arg
            when :running, :r then
              filter[:state] <<= 'running'
            when :stopped, :s then
              filter[:state] <<= 'stopped'
            when :unknown then
              filter[:state] <<= 'unknown'
          end
        end

        opts.separator ''
        opts.separator <<-NOTES
Notes:
    - A filter is required for all commands except for show
    - Filters are evaluated as: set | host | ip | state | (binary & config)
    - The HOST, BINARY, and CONFIG arguments are globs
    - BINARY format is groupId:artifactId[:packaging[:classifier]]:version
    - CONFIG format is @env:component[:pools]:version
    - The default filter selects all hosts
NOTES
        opts.separator ''
        opts.separator 'Commands:'
        opts.separator "  #{COMMANDS.join("\n  ")}"
      end

      option_parser.parse!(args)

      puts options.map { |k, v| "#{k}=#{v}" }.join("\n") if options[:debug]
      puts filter.map { |k, v| v.map { |v1| "#{k}=#{URI.escape(v1)}" }.join('\n') }.join('\n')

      if args.length == 0 then
        puts option_parser
        exit EXIT_CODES[:success]
      end

      command = args[0].to_sym

      is_agent = false
      if command == :agent then
        args = args.drop(1)
        if args.length == 0 then
          puts option_parser
          exit EXIT_CODES[:success]
        end
        command = "#{command}_#{args[0]}".to_sym
        is_agent = true
      end

      unless COMMANDS.include?(command)
        raise CommandError.new(:invalid_usage, "Unsupported command: #{command}")
      end

      if options[:coordinator_url].nil? || options[:coordinator_url].empty?
        raise CommandError.new(:invalid_usage, "You must set Galaxy coordinator host by passing --coordinator COORDINATOR or by setting the GALAXY_COORDINATOR environment variable.")
      end

      return [command, filter, options, is_agent, args.drop(1)]
    end

    def self.execute(args)
      begin
        (command, filter, options, is_agent, command_args) = parse_command_line(args)
        result = Commands.send(command, filter, options, command_args)
        if !is_agent  then
          slots = result.sort_by { |slot| [slot.ip, slot.binary || '', slot.config || '', slot.uuid] }
          puts '' if options[:debug]

          names = ['uuid', 'ip', 'status', 'binary', 'config', '']
          if STDOUT.tty?
            format = slots.map { |slot| slot.columns }.
                           map { |cols| cols.map(&:size) }.
                           transpose.
                           map(&:max).
                           map { |size| "%-#{size}s" }
            format[-1] = "%s"
            format = format.join('  ')

            puts Colorize::colorize(format % names, :bright, :cyan)
          else
            format = names.map { "%s" }.join("\t")
          end

          slots.each { |slot| puts format % slot.columns(STDOUT.tty?) }
        else
          agents = result.sort_by { |agent| [agent.ip, agent.agent_id] }
          puts '' if options[:debug]

          names = ['id', 'ip', 'status', 'type', 'location']
          if STDOUT.tty?
            format = agents.map { |agent| agent.columns }.
                           map { |cols| cols.map(&:size) }.
                           transpose.
                           map(&:max).
                           map { |size| "%-#{size}s" }.
                           join('  ')

            puts Colorize::colorize(format % names, :bright, :cyan)
          else
            format = names.map { "%s" }.join("\t")
          end

          agents.each { |agent| puts format % agent.columns(STDOUT.tty?) }
        end


        exit EXIT_CODES[:success]
      rescue Errno::ECONNREFUSED
        puts "Coordinator refused connection: #{options[:coordinator_url]}"
        exit EXIT_CODES[:general_error]
      rescue CommandError => e
        puts e.message
        if e.code == :invalid_usage
          puts ''
          parse_command_line([])
        end
        if options[:debug]
          puts ''
          puts "exit: #{e.code}"
        end
        exit EXIT_CODES[e.code || :general_error]
      end
    end

  end
end


if __FILE__ == $0 then
  $:.unshift File.join(File.dirname(__FILE__), *%w[.. lib])
  Galaxy::CLI.execute(ARGV)
end
