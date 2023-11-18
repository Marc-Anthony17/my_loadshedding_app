package wethinkcode.places;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collection;
import java.util.Properties;

import com.google.common.annotations.VisibleForTesting;
import io.javalin.Javalin;
import io.javalin.http.Context;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import wethinkcode.places.model.Places;
import wethinkcode.places.model.Town;

/**
 * I provide a Place-names Service for places in South Africa.
 * <p>
 * I read place-name data from a CSV file that I read and
 * parse into the objects (domain model) that I use,
 * discarding unwanted data in the file (things like mountain/river names). With my "database"
 * built, I then serve-up place-name data as JSON to clients.
 * <p>
 * Clients can request:
 * <ul>
 * <li>a list of available Provinces
 * <li>a list of all Towns/PlaceNameService in a given Province
 * <li>a list of all neighbourhoods in a given Town
 * </ul>
 * I understand the following command-line arguments:
 * <dl>
 * <dt>-c | --config &lt;configfile&gt;
 * <dd>a file pathname referring to an (existing!) configuration file in standard Java
 *      properties-file format
 * <dt>-d | --datadir &lt;datadirectory&gt;
 * <dd>the name of a directory where CSV datafiles may be found. This option <em>overrides</em>
 *      and data-directory setting in a configuration file.
 * <dt>-p | --places &lt;csvdatafile&gt;
 * <dd>a file pathname referring to a CSV file of place-name data. This option
 *      <em>overrides</em> any value in a configuration file and will bypass any
 *      data-directory set via command-line or configuration.
 */
@Command( name = "PlaceNameService", mixinStandardHelpOptions = true )
public class PlaceNameService implements Runnable {

    public static final int DEFAULT_PORT = 7000;

    // Configuration keys
    public static final String CFG_CONFIG_FILE = "config.file";
    public static final String CFG_DATA_DIR = "data.dir";
    public static final String CFG_DATA_FILE = "data.file";
    public static final String CFG_SERVICE_PORT = "server.port";

    public static void main( String[] args ){
        final PlaceNameService svc = new PlaceNameService().initialise();
        final int exitCode = new CommandLine( svc ).execute( args );
        System.exit( exitCode );
    }

    // Instance state

    private final Properties config;

    private Javalin server;

    private Places places;

    // FIXME: Command-line options. I don't like that these are in the PlaceNameService
    // where they might easily get (mis)used instead of the access methods
    // (configFile(), dataFile() and dataDir()) down below. BUT: can the `picocli`
    // library deal with them properly if they're in an inner/nested class (or something)?
    // I haven't the time to discover this right now.

    @Option( names = { "-c", "--config" }, description = "Configuration file path" )
    private File configFile;

    @Option( names = { "-d", "--datadir" },
             description = "Directory pathname where datafiles may be found" )
    private File dataDir;

    @Option( names = { "-f", "--datafile" }, description = "CSV Data file path" )
    private File dataFile;

    @Option( names = { "-p", "--port" }, description = "Service network port number" )
    private int svcPort;

    public PlaceNameService(){
        config = initConfig();
    }

    public void start(){
        start( servicePort() );
    }

    @VisibleForTesting
    void start( int port ){
        server.start( port );
    }

    public void stop(){
        server.stop();
    }

    /**
     * Why not put all of this into the constructor? Well, this way makes
     * it easier (possible!) to test an instance of PlaceNameService without
     * starting up all the big machinery (i.e. without calling initialise()).
     */
    @VisibleForTesting
    PlaceNameService initialise(){
        places = initPlacesDb();
        server = initHttpServer();
        return this;
    }

    /**
     * Sometimes we want to initialise with test data...
     */
    @VisibleForTesting
    PlaceNameService initialise( Places aPlaceDb ){
        places = aPlaceDb;
        server = initHttpServer();
        return this;
    }

    @Override
    public void run(){
        server.start( servicePort() );
    }

    /**
     * Initialise the service configuration from either a config file specified on
     * the command-line or a default configuration-file
     * ({@code $WORKING_DIRECTORY/places.properties}),
     * then override those if specific config values have been given on the command-line.
     *
     * @return a non-null Properties instance
     */
    private Properties initConfig(){
        try( FileReader in = new FileReader( configFile() )){
            final Properties p = new Properties( defaultConfig() );
            p.load( in );
            return p;
        }catch( IOException ex ){

            // We can recover from this, but (maybe later) we really
            // ought to notify somebody that there's a problem.

            return defaultConfig();
        }
    }

    private Places initPlacesDb(){
        try{
            return new PlacesCsvParser().parseCsvSource( dataFile() );
        }catch( IOException ex ){

            // FIXME: We really ought to be able to do better than this!
            System.err.println( "Error reading CSV file " + dataFile + ": " + ex.getMessage() );
            throw new RuntimeException( ex );
        }
    }

    private Javalin initHttpServer(){
        return Javalin.create()
            .get( "/provinces", ctx -> ctx.json( places.provinces() ))
            .get( "/towns/{province}", this::getTowns );
    }

    private Context getTowns( Context ctx ){
        final String province = ctx.pathParam( "province" );
        final Collection<Town> towns = places.townsIn( province );
        return ctx.json( towns );
    }

    @VisibleForTesting
    File configFile(){
        return configFile != null
            ? configFile
            : new File( defaultConfig().getProperty( CFG_CONFIG_FILE ));
    }

    @VisibleForTesting
    File dataFile(){
        return dataFile != null
            ? dataFile
            : new File( getConfig( CFG_DATA_FILE ));
    }

    @VisibleForTesting
    File dataDir(){
        return dataDir != null
            ? dataDir
            : new File( getConfig( CFG_DATA_DIR ));
    }

    int servicePort(){
        return svcPort > 0
            ? svcPort
            : Integer.valueOf( getConfig( CFG_SERVICE_PORT ));
    }

    @VisibleForTesting
    String getConfig( String aKey ){
        return config.getProperty( aKey );
    }

    @VisibleForTesting
    Places getDb(){
        return places;
    }

    private static Properties defaultConfig(){
        final Properties p = new Properties();
        p.setProperty( CFG_CONFIG_FILE, System.getProperty( "user.dir" ) + "/places.properties" );
        p.setProperty( CFG_DATA_DIR, System.getProperty( "user.dir" ));
        p.setProperty( CFG_DATA_FILE, System.getProperty( "user.dir" ) + "/places.csv" );
        p.setProperty(CFG_SERVICE_PORT, Integer.toString(DEFAULT_PORT ));
        return p;
    }
}