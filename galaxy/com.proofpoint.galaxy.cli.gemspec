# create by maven - leave it as is
Gem::Specification.new do |s|
  s.name = 'com.proofpoint.galaxy.cli'
  s.version = "@GALAXY_GEM_VERSION@"

  s.summary = 'galaxy'
  s.description = 'Galaxy command line interface'
  s.homepage = 'https://github.com/dain/galaxy-server'

  s.authors = ['Dain Sundstrom']
  s.email = ['dain@iq80.com']

  s.rubyforge_project = 'galaxy'
  s.files = Dir['bin/**/*']
  s.files += Dir['lib/**/*']
  s.executables << 'galaxy'
  s.add_dependency 'httpclient', '>=2.2.0'
  s.add_dependency 'json_pure', '>=1.5.1'
end
