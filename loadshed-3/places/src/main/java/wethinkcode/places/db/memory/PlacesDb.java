package wethinkcode.places.db.memory;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import wethinkcode.places.model.Places;
import wethinkcode.places.model.Town;

/**
 * TODO: javadoc PlacesDb
 */
public class PlacesDb implements Places
{
    private final Set<Town> towns = new TreeSet<>();

    public PlacesDb( Set<Town> places ){
        towns.addAll( places );
    }

    @Override
    public Collection<String> provinces(){
        return towns.parallelStream()
            .map( town -> town.getProvince() )
            .collect( Collectors.toSet() );
    }

    @Override
    public Collection<Town> townsIn( String aProvince ){
        return towns.parallelStream()
            .filter( aTown -> aTown.getProvince().equals( aProvince ))
            .collect( Collectors.toSet() );
    }

    @Override
    public int size(){
        return towns.size();
    }

}