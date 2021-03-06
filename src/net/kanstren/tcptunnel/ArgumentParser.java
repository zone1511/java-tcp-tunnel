package net.kanstren.tcptunnel;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses command line argument and options.
 * 
 * @author Teemu Kanstren.
 */
public class ArgumentParser {
  /** System specific line separator. */
  private static String ln = System.getProperty("line.separator");

  /**
   * Parses the given arguments and options.
   * 
   * @param args The arguments and options to parse. An option is expected to start with "--".
   * @return The configuration parsed from the arguments/options.
   */
  public static Params parseArgs(String[] args) {
    //in case the program is invoked with no arguments, we print the help
    if (args.length == 0) args = new String[] {"--help"};
    Params params = new Params();
    //we collect all errors and if any are found, report them all in the end
    String errors = "";
    //collect all options (starting with "--") here
    List<Option> options = new ArrayList<>();
    //collect all non-option parameters here
    List<String> paramStrs = new ArrayList<>();
    for (int i = 0 ; i < args.length ; i++) {
      String arg = args[i];
      if (arg.startsWith("--")) {
        if (arg.equals("--hex")) {
          //hex is a standalone option, so give it a value and move straight to next one
          options.add(new Option("--hex", "true"));
          continue;
        }
        if (arg.equals("--help")) {
          //help is a standalone option, so give it a value and move straight to next one
          options.add(new Option("--help", "true"));
          continue;
        }
        if (args.length <= i + 1) {
          //all options coming this far should have a value. otherwise it is an error.
          errors += "No value given for option " + arg + ". Please provide one." + ln;
          break;
        }
        Option option = new Option(arg, args[i + 1]);
        options.add(option);
        i++;
      } else {
        paramStrs.add(arg);
      }
    }
    //now that we collected all the options, parse the actual content from their values and check they are valid
    errors = parseOptions(options, params, errors);

    //if the options were ok, move to parsing and checking the non-option parameters
    if (params.shouldRun()) {
      boolean paramsOK = true;
      if (paramStrs.size() < 3) {
        errors += "Too few arguments. Need 3, got " + paramStrs.size() + ": " + paramStrs + "." + ln;
        paramsOK = false;
      }
      if (paramStrs.size() > 3) {
        errors += "Too many arguments. Need 3, got " + paramStrs.size() + ": " + paramStrs+ "." + ln;
        paramsOK = false;
      }
      if (paramsOK) errors = parseParams(paramStrs, params, errors);
    }
    
    //every error msg ends with a linefeed, so remove the last one
    if (errors.length() > 0) errors = errors.substring(0, errors.length() - ln.length());
    params.setErrors(errors);
    return params;
  }

  /**
   * Parse the actual content from the previously captured option definitions.
   * 
   * @param options The options from which to parse the content.
   * @param params This is where we store the parsed option content.
   * @param errors The errors previously observed.
   * @return Updated errors with the old one intact and new ones added.
   */
  private static String parseOptions(List<Option> options, Params params, String errors) {
    //we set this to true if the --hex option is given
    boolean hex = false;
    //this is to trace if any loggers are defined. if not, we add the default later.
    int loggers = 0;
    for (Option option : options) {
      String name = option.name;
      switch (name) {
        case "--buffersize":
          //here we have the buffersize to use for capturing the observed stream and for writing to files etc.
          try {
            int bufferSize = Integer.parseInt(option.value);
            params.setBufferSize(bufferSize);
            if (bufferSize <= 0) errors += "Buffer size has to be > 0, was: " + bufferSize + "." + ln;
          } catch (NumberFormatException e) {
            errors += "Invalid number for 'buffersize':" + option.value + "." + ln;
          }
          break;
        case "--encoding":
          //here we have the character encoding used to decode strings from raw bytes
          params.setEncoding(option.value);
          try {
            boolean supported = Charset.isSupported(option.value);
            if (!supported) errors += "Unsupported encoding: '" + option.value + "'." + ln;
          } catch (Exception e) {
            errors += "Unsupported encoding: '" + option.value + "'." + ln;
          }
          break;
        case "--down":
          //this is the path for storing the downstream traffic (from remote host to local port connection)
          params.setDownFilePath(option.value);
          break;
        case "--up":
          //this is the path for storing the upstream traffic (from local port to remote host)
          params.setUpFilePath(option.value);
          break;
        case "--hex":
          //if using byte console logger, this setting changes printing the bytes from integer list to set of hex characters
          hex = true;
          break;
        case "--help":
          //obviously, print out the help
          System.out.println(help());
          params.setShouldRun(false);
          return "";
        case "--logger":
          //increase the number of loggers found so we know later if we need to create the default one or not
          loggers++;
          //handle creation in round 2
          break;
        default:
          //anything not processed above is invalid..
          errors += "Invalid option '" + name + "'." + ln;
          break;
      }
    }
    //add the default logger if none found before
    if (loggers == 0) options.add(new Option("--logger", "console-string"));
    for (Option option : options) {
      //we create loggers in this separate iteration to have access to affecting parameters (e.g., --hex) and default logger if none specified by user
      String name = option.name;
      switch (name) {
        case "--logger":
          errors = addLogger(params, option.value, hex, errors);
          break;
      }
    }
    return errors;
  }

  /**
   * Adds a given type of logger to parser results.
   * 
   * @param params For storing parsed parameters (logger instances).
   * @param type The type identifier for the logger.
   * @param hex Whether to print bytes as ints or hex in relevant loggers.
   * @param errors Errors so far.
   * @return Previous and new errors.
   */
  private static String addLogger(Params params, String type, boolean hex, String errors) {
    switch(type) {
      case "console-string":
        //print decoded strings to console
        params.enableStringConsoleLogger();
        break;
      case "console-bytes":
        //print bytes to console, hex or int
        params.enableByteConsoleLogger(hex);
        break;
      case "file-string":
        //write decoded strings to file
        try {
          params.enableStringFileLogger(params.getDownFilePath(), params.getUpFilePath());
        } catch (IOException e) {
          errors += "Unable to create string file logger:"+e.getMessage();
          e.printStackTrace();
        }
        break;
      case "file-bytes":
        //write raw bytes as binary to file
        try {
          params.enableByteFileLogger(params.getDownFilePath(), params.getUpFilePath());
        } catch (IOException e) {
          errors += "Unable to create byte file logger:"+e.getMessage();
        }
        break;
      default:
        //anything not processed above is considerd invalid
        errors += "Unknown logger type: '"+type+"'."+ln;
    }
    return errors;
  }

//  private String checkFileAcces(String path, String errors) {
//    try {
//      File file = new File(path);
//      file.createNewFile();
//      file.delete();
//    } catch(IOException e) {
//      errors += "Unable to create file: '"+path+"'."+ln;
//    }
//    return errors;
//  }

  /**
   * Parse the actual parameters, meaning source (local) port, remote host and remote port.
   * Parameters are the ones not starting with '-'.
   * 
   * @param paramStrs The strings for parameters, filtered from options.
   * @param params For storing the parsing results.
   * @param errors Previous parsing errors so far.
   * @return Previous and new parsing errors.
   */
  private static String parseParams(List<String> paramStrs, Params params, String errors) {
    try {
      int sourcePort = Integer.parseInt(paramStrs.get(0));
      params.setSourcePort(sourcePort);
      if (sourcePort < 1 || sourcePort > 65535) errors += "Port numbers have to be in range 1-65535, source port was: " + sourcePort + "." + ln;
    } catch (NumberFormatException e) {
      errors += "Unable to parse source port from: '" + paramStrs.get(0) + "'." + ln;
    }
    params.setRemoteHost(paramStrs.get(1));
    try {
      int remotePort = Integer.parseInt(paramStrs.get(2));
      params.setRemotePort(remotePort);
      if (remotePort < 1 || remotePort > 65535) errors += "Port numbers have to be in range 1-65535, remote port was: " + remotePort + "." + ln;
    } catch (NumberFormatException e) {
      errors += "Unable to parse remote port from: '" + paramStrs.get(2) + "'." + ln;
    }
    return errors;
  }

  /**
   * @return The help text to show to user.
   */
  public static String help() {
    return "A program for capturing data sent and received between two points. A proxy. A MITM. A whatever." + ln +
            "Nothing fancy, just basic capture of data. No certificate handling etc." + ln + ln +
            "Usage: java -jar tcptunnel.jar [options] <sourceport> <remotehost> <remoteport>" + ln +
            "Parameters:" + ln +
            "  <sourceport> : The port to bind and wait for connections on localhost." + ln +
            "  <remotehost> : The host to connect to and forward traffic when someone connects to <localport>." + ln +
            "  <remoteport> : The port on the <remotehost> to connect to and forward traffic when someone connects to <localport>." + ln +
            ln +
            "Options:" + ln +
//            "" + ln +
            "  --buffersize <bytes> : Size of input buffer used to read data in bytes. Defaults to " + Params.DEFAULT_BUFFER_SIZE + " bytes." + ln +
            "  --encoding <encoding> : Use the given encoding to decode strings. Default is " + Params.DEFAULT_ENCONDING + "." + ln +
            "  --down <path> : Write remote->local data stream to file in <path>. Default is " + Params.DEFAULT_DOWN_PATH + ". Suffix is logger dependent." + ln +
            "  --up <path> : Write local->remote data stream to file in <path>. Default is " + Params.DEFAULT_UP_PATH + ". Suffix is logger dependent." + ln +
            "  --logger <type> : Add specified type of logger. Default is 'console-string'." + ln +
            "  --hex <true/false> : If using a console-bytes logger, defines whether to convert bytes to hex or int in printed lists." + ln +
            "  --help : Prints this help and exits." + ln + ln +
            "Loggers types:" + ln +
            "  console-string: Prints logged data as strings (in defined encoding). For upstream to system.out and downstream to system.err." + ln +
            "  console-bytes: Prints logged data as list of byte values. For upstream to system.out and for downstream to system.err." + ln +
            "  file-string: Prints as strings (in defined encoding). To defined output files with .txt ending." + ln +
            "  file-bytes: Writes logged data as list of byte values. To defined output files with .bytes ending.";
  }

  /**
   * A class for holding a name-value pair as a configuration option.
   */
  private static class Option {
    /** The option name. */
    public final String name;
    /** The unparsed option value.*/
    public final String value;

    /**
     * @param name The option name.
     * @param value The unparsed option value.
     */
    public Option(String name, String value) {
      this.name = name;
      this.value = value;
    }
  }
}
