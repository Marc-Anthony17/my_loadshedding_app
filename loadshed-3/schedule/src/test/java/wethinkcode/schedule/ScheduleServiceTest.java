package wethinkcode.schedule;

import java.util.Optional;

import org.junit.jupiter.api.*;
import wethinkcode.loadshed.common.transfer.ScheduleDO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ScheduleServiceTest
{
    private ScheduleService testSvc;

    @BeforeEach
    public void initTestScheduleFixture(){
        testSvc = new ScheduleService();
    }

    @AfterEach
    public void destroyTestFixture(){
        testSvc = null;
    }

    @Test
    public void testSchedule_someTown(){
        final Optional<ScheduleDO> schedule = testSvc.getSchedule( "Eastern Cape", "Gqeberha", 4 );
        assertThat( schedule.isPresent() );
        assertEquals( 4, schedule.get().numberOfDays() );
    }

    @Test
    public void testSchedule_nonexistentTown(){
        final Optional<ScheduleDO> schedule = testSvc.getSchedule( "Mars", "Elonsburg", 2 );
        assertThat( schedule.isEmpty() );
    }
}
