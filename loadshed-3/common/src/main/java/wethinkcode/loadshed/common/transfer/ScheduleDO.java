package wethinkcode.loadshed.common.transfer;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

// The following nested classes are the "domain model" for this service.

// Note that a real model would be a bit more sophisticated and complex,
// and we'd want to put the source in its own package;
// this model is just a dummy "the minimum thing that could possible work"
// so that we can stay focussed on service interconnections and communication
// without getting bogged down in modelling.
public class ScheduleDO
{
    // Using LocalDate.now() is probably a bug! Is the server in the
    // same timezone as the client? Maybe not.
    // What if the server creates an instance one nanosecond before midnight
    // before sending it to the client who then gets it "the next day"?
    // What could you do about all that?
    private LocalDate startDate = LocalDate.now();

    private List<DayDO> loadSheddingDays;

    public ScheduleDO(){
    }

    @JsonCreator
    public ScheduleDO(
        @JsonProperty( value = "days" ) List<DayDO> days ){
        loadSheddingDays = days;
    }

    public List<DayDO> getDays(){
        return loadSheddingDays;
    }

    public int numberOfDays(){
        return getDays().size();
    }

    public LocalDate getStartDate(){
        return startDate;
    }

}
