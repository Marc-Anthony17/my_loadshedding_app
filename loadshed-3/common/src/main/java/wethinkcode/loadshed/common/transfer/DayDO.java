package wethinkcode.loadshed.common.transfer;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * TODO: javadoc DayDO
 */
public class DayDO
{
    private List<SlotDO> loadSheddingSlots;

    public DayDO(){
    }

    @JsonCreator
    public DayDO(
        @JsonProperty( value = "slots" ) List<SlotDO> slots ){
        loadSheddingSlots = slots;
    }

    public List<SlotDO> getSlots(){
        return loadSheddingSlots;
    }

    public int numberOfSlots(){
        return getSlots().size();
    }

}
