package wethinkcode.places;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;
import wethinkcode.places.db.memory.PlacesDb;
import wethinkcode.places.model.Places;
import wethinkcode.places.model.Town;

import static java.util.Objects.requireNonNull;

/**
 * PlacesCsvParser : I parse a CSV file with each line containing the fields (in order):
 * <code>Name, Feature_Description, pklid, Latitude, Longitude, Date, MapInfo, Province,
 * fklFeatureSubTypeID, Previous_Name, fklMagisterialDistrictID, ProvinceID, fklLanguageID,
 * fklDisteral, Local Municipality, Sound, District Municipality, fklLocalMunic, Comments, Meaning</code>.
 * <p>
 * For the PlaceNameService we're only really interested in the <code>Name</code>,
 * <code>Feature_Description</code> and <code>Province</code> fields.
 * <code>Feature_Description</code> allows us to distinguish towns and urban areas from
 * (e.g.) rivers, mountains, etc. since our PlaceNameService is only concerned with occupied places.
 */
public class PlacesCsvParser
{
    public Places parseCsvSource( File csvFile ) throws FileNotFoundException, IOException {
        //{snip/1}
        requireNonNull( csvFile );
        if( ! (csvFile.exists() && csvFile.canRead() )){
            throw new FileNotFoundException( "Required CSV input file " + csvFile.getPath() + " not found." );
        }

        return parseCsvSource( new LineNumberReader(
            new FileReader( csvFile )
        ));
        //{/snip}
    }

    //{snip/1}
    public Places parseCsvSource( LineNumberReader reader ) throws IOException {
        try( final LineNumberReader in = Objects.requireNonNull( reader )){
            in.readLine();  // get rid of the header line
            final Places db = parseDataLines( in );
            in.getLineNumber();
            return db;
        }
    }

    @VisibleForTesting
    Places parseDataLines( final LineNumberReader in ){
        final Set<Town> allTowns = in.lines()
            .map( this::splitLineIntoValues )
            .filter(this::isLineAWantedFeature )
            .map( this::asTown )
            .collect( Collectors.toSet() );
        return new PlacesDb( allTowns );
    }

    // The following variables are only useful in the methods that follow them...

    private static final Set<String> WANTED_FEATURES = Set.of(
        "Urban Area".toLowerCase(),
        "Town".toLowerCase(),
        "Township".toLowerCase() );

    static final int NAME_COLUMN = 0;
    static final int FEATURE_COLUMN = 1;
    static final int PROVINCE_COLUMN = 7;
    static final int MIN_COLUMNS = PROVINCE_COLUMN + 1;
    static final int MAX_COLUMNS = 20;

    @VisibleForTesting
    boolean isLineAWantedFeature( String[] csvValue ){
        return WANTED_FEATURES.contains( csvValue[FEATURE_COLUMN].toLowerCase() );
    }

    @VisibleForTesting
    String[] splitLineIntoValues( String aCsvLine ){

        // There is a small potential problem with using String::split here:
        // split() ignores empty values at the end of the input String rather than
        // explicitly assigning null for those columns. This means we're never really
        // sure how many columns (values) were actually in the input String. For the
        // data we're currently dealing with, it doesn't matter -- the stuff we want
        // is in the first few columns, but this could become a problem if the data
        // format changes...
        //
        // The alternative to using split() would be to parse the input "by hand".
        // That's tedious and error-prone.

        final String[] v = aCsvLine.trim().split( "," );
        return v;
    }

    @VisibleForTesting
    Town asTown( String[] values ){
        return new Town( values[NAME_COLUMN], values[PROVINCE_COLUMN]);
    }
    //{/snip}
}
