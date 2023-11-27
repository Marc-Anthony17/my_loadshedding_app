package wethinkcode.schedule;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.plugin.bundled.CorsPluginConfig;
import org.apache.activemq.ActiveMQConnectionFactory;
import wethinkcode.loadshed.common.mq.MQ;
import wethinkcode.loadshed.common.transfer.DayDO;
import wethinkcode.loadshed.common.transfer.ScheduleDO;
import wethinkcode.loadshed.common.transfer.SlotDO;

import javax.jms.*;

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

    private Connection connection;

    private int newStage = 0;
    public static void main( String[] args ){
        final ScheduleService svc = new ScheduleService().initialise();
        svc.start();
    }


    @VisibleForTesting
    public ScheduleService initialise(){
        listener();
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
        return Javalin.create((config -> { config.plugins.enableCors(cors -> {
                    // allow any host to access your resources
                    cors.add(CorsPluginConfig::anyHost);
                });
                }))
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

        final String province = ctx.pathParam( "province" );
        final String townName = ctx.pathParam( "town" );

        if( province.isEmpty() || townName.isEmpty() ){
            ctx.status( HttpStatus.BAD_REQUEST );
            return ctx;
        }
        if( newStage < 0 || newStage > 8 ){
            return ctx.status( HttpStatus.BAD_REQUEST );
        }

        final Optional<ScheduleDO> schedule = getSchedule( province, townName, newStage );

        ctx.status( schedule.isPresent()
                ? HttpStatus.OK
                : HttpStatus.NOT_FOUND );
        return ctx.json( schedule.orElseGet( ScheduleService::emptySchedule ) );
    }

    // There *must* be a better way than this...
    Optional<ScheduleDO> getSchedule( String province, String town, int stage ){
        return !isValidProvince(province)
            ? Optional.empty()
            : Optional.of( mockSchedule() );
    }

    private boolean isValidProvince(String province) {
        for (String validProvince : makeProvince()) {
            if (validProvince.equalsIgnoreCase(province)) {
                return true;
            }
        }
        return false;
    }
    private ArrayList<String> makeProvince(){
        ArrayList<String> provinces = new ArrayList<>();
        provinces.add("Eastern Cape");
        provinces.add("Free State");
        provinces.add("Gauteng");
        provinces.add("KwaZulu-Natal");
        provinces.add("Limpopo");
        provinces.add("Mpumalanga");
        provinces.add("North West");
        provinces.add("Northern Cape");
        provinces.add("Western Cape");
        return provinces;
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




    public void listener(){
        try{
            final ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory( MQ.URL );
            connection = factory.createConnection( MQ.USER, MQ.PASSWD );

            final Session session = connection.createSession( false, Session.AUTO_ACKNOWLEDGE );
            final Destination dest = session.createTopic( MQ_TOPIC ); // <-- NB: Topic, not Queue!

            final MessageConsumer receiver = session.createConsumer( dest );
            receiver.setMessageListener( new MessageListener(){
             @Override
             public void onMessage( Message m ){
                 try {
                     String body = ((TextMessage) m).getText();

                     int theStage = Integer.parseInt(body.split(" ")[2]);
                     if ("SHUTDOWN".equals(body)) {
                         connection.close();
                     }
                     if(theStage >= 0 && theStage <9){
                         newStage = theStage;
                     }
                     System.out.println("Received message: " + body);
                 }catch (Exception e) {
                     System.out.println("invalid option");
                     throw new RuntimeException(e);
                 }
             }
         }
            );
            connection.start();

        }catch( JMSException erk ){
            throw new RuntimeException( erk );
        }
    }
}
