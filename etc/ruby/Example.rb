# This example shows how database commands can be executed.
# Documentation: http://basex.org/api
#
# (C) BaseX Team 2005-11, BSD License

require 'BaseXClient.rb'

begin
  # initialize timer
  start_time = Time.now

  # create session
  session = Session.new("localhost", 1984, "admin", "admin")

  # perform command and print returned string
  print session.execute("xquery 1 to 10")

  # close session
  session.close

  # print time needed
  time = (Time.now - start_time) * 1000
  puts " #{time} ms."

rescue Exception => e
  # print exception
  puts e
end
