module Colorize
  module ANSIColors
    COLORS = {
      :normal => "\e[39m",
      :bright => "\e[1m",

      :red => "\e[31m",
      :green => "\e[32m",
      :blue => "\e[34m",
      :cyan => "\e[36m"
    }
  end

  def self.colorize(string, *colors)
    color_string = colors.map { |color| ANSIColors::COLORS[color] }.join

    return "#{color_string}#{string}#{ANSIColors::COLORS[:normal]}"
  end
end