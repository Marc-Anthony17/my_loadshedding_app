package wethinkcode.schedule;

import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import wethinkcode.loadshed.common.transfer.DayDO;
import wethinkcode.loadshed.common.transfer.ScheduleDO;
import wethinkcode.loadshed.common.transfer.SlotDO;

/**
 * I provide a REST API providing the current loadshedding schedule for a given town (in a specific province) at a given
 * loadshedding stage.
 */
public class ScheduleService
{
    public static final int DEFAULT_STAGE = 0; // no loadshedding. Ha!

    public static final int DEFAULT_PORT = 7002;

    public static final String MQ_TOPIC = "stage";

    private Javalin server;

    private int servicePort;

    public static void main( String[] args ){
        final ScheduleService svc = new ScheduleService().initialise();
        svc.start();
    }

    @VisibleForTesting
    ScheduleService initialise(){
        server = initHttpServer();
        return this;
    }

    public void start(){
        start( DEFAULT_PORT );
    }

    @VisibleForTesting
    void start( int networkPort ){
        servicePort = networkPort;
        run();
    }

    public void stop(){
        server.stop();
    }

    public void run(){
        server.start( servicePort );
    }

    private Javalin initHttpServer(){
        return Javalin.create()
            .get( "/{province}/{town}/{stage}", this::getSchedule )
            .get( "/{province}/{town}", this::getDefaultSchedule );
    }

    private Context getSchedule( Context ctx ){
        final String province = ctx.pathParam( "province" );
        final String townName = ctx.pathParam( "town" );
        final String stageStr = ctx.pathParam( "stage" );

        if( province.isEmpty() || townName.isEmpty() || stageStr.isEmpty() ){
            ctx.status( HttpStatus.BAD_REQUEST );
            return ctx;
        }
        final int stage = Integer.parseInt( stageStr );
        if( stage < 0 || stage > 8 ){
            return ctx.status( HttpStatus.BAD_REQUEST );
        }

        final Optional<ScheduleDO> schedule = getSchedule( province, townName, stage );

        ctx.status( schedule.isPresent()
            ? HttpStatus.OK
            : HttpStatus.NOT_FOUND );
        return ctx.json( schedule.orElseGet( ScheduleService::emptySchedule ) );
    }

    private Context getDefaultSchedule( Context ctx ){
        throw new UnsupportedOperationException( "TODO" );
    }

    // There *must* be a better way than this...
    Optional<ScheduleDO> getSchedule( String province, String town, int stage ){
        return province.equalsIgnoreCase( "Mars" )
            ? Optional.empty()
            : Optional.of( mockSchedule() );
    }

    private static ScheduleDO mockSchedule(){
        final List<SlotDO> slots = List.of(
            new SlotDO( LocalTime.of( 2, 0 ), LocalTime.of( 4, 0 ) ),
            new SlotDO( LocalTime.of( 10, 0 ), LocalTime.of( 12, 0 ) ),
            new SlotDO( LocalTime.of( 18, 0 ), LocalTime.of( 20, 0 ) )
        );
        final List<DayDO> days = List.of(
            new DayDO( slots ),
            new DayDO( slots ),
            new DayDO( slots ),
            new DayDO( slots )
        );
        return new ScheduleDO( days );
    }

    private static ScheduleDO emptySchedule(){
        final List<SlotDO> slots = Collections.emptyList();
        final List<DayDO> days = Collections.emptyList();
        return new ScheduleDO( days );
    }
}
