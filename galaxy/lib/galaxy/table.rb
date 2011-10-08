require 'galaxy/colorize'

class Table
  def initialize(headers = nil, rows = nil)
    @headers = headers
    @rows = rows || []
  end

  def <<(row)
    @rows << row
  end

  def calculate_widths
    (@rows + [@headers]).
            map { |cols| cols.map { |col| Colorize::strip_colors(col) }.map(&:size) }.
            transpose.
            slice(0..-2).  # don't pad last column
            map(&:max)
  end

  def render(tty = true)
    widths = calculate_widths

    rows = @rows
    rows = [@headers] + rows if tty

    rows.map { |row| render_row(row, widths, tty) }.join("\n")
  end

  def render_row(row, col_widths, tty)
    if tty
      row.zip(col_widths).map do |value, width|
        width ||= Colorize.strip_colors(value).length
        value + ' ' * (width - Colorize.strip_colors(value).length)
      end.join('  ')
    else
      row.map { |value| Colorize.strip_colors(value) }.join("\t")
    end
  end
end


if __FILE__ == $0
  table = Table.new(['uuid', 'ip', 'status', 'binary', 'config', ''])

  table <<  [Colorize::colorize('1' * 6, :green), '2' * 5, '3' * 10, '4' * 10, '5' * 5, '6' * 3]
  table <<  ['1' * 6, '2' * 5, '3' * 15, '4' * 7, '5' * 5, '6' * 10]

  puts table.render(true)
end