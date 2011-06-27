module Colorize
  module ANSIColors
    COLORS = {
      :normal => "\e[0m",
      :bright => "\e[1m",

      :red => "\e[31m",
      :green => "\e[32m"
    }
  end

  def self.colorize(string, *colors)
    color_string = colors.map { |color| ANSIColors::COLORS[color] }.join

    return "#{color_string}#{string}#{ANSIColors::COLORS[:normal]}"
  end
end