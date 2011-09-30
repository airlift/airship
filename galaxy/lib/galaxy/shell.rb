module Shell
  def self.quote(string)
    string = string.gsub("'", %q('\\\''))
    "'#{string}'"
  end
end