package wethinkcode.web;

import com.google.common.annotations.VisibleForTesting;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import kong.unirest.HttpResponse;
import kong.unirest.HttpStatus;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import org.apache.activemq.ActiveMQConnectionFactory;
import wethinkcode.loadshed.common.mq.MQ;
import wethinkcode.loadshed.common.transfer.StageDO;
import wethinkcode.places.PlaceNameService;
import wethinkcode.schedule.ScheduleService;
import wethinkcode.stage.StageService;

import javax.jms.*;

/**
 * I am the front-end web server for the LightSched project.
 * <p>
 * Remember that we're not terribly interested in the web front-end part of this server, more in the way it communicates
 * and interacts with the back-end services.
 */
public class WebService
{

    public static final int DEFAULT_PORT = 8080;

    public static final String MQ_TOPIC_NAME = "stage";
    public static final String STAGE_SVC_URL = "http://localhost:" + StageService.DEFAULT_PORT;

    public static final String PLACES_SVC_URL = "http://localhost:" + PlaceNameService.DEFAULT_PORT;

    public static final String SCHEDULE_SVC_URL = "http://localhost:" + ScheduleService.DEFAULT_PORT;

    private static final String PAGES_DIR = "/templates";

    public static void main( String[] args ){
        final WebService svc = new WebService().initialise();
//        svc.start();
    }

    private Javalin server;

    private int servicePort;

    private Connection connection;

    @VisibleForTesting
    WebService initialise(){
        server = configureHttpServer();
        launchAllServers();
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
        launchAllServers(servicePort);
    }

//    private void configureHttpClient(){
//        throw new UnsupportedOperationException( "TODO" );
//    }

    private void launchAllServers(){
        configurePlaceNameService();
        configureScheduleService();
        server.start( DEFAULT_PORT );
        listener();
        configureStageService();
    }
    private void launchAllServers(int selectedPort){
        configurePlaceNameService();
        configureScheduleService();
        server.start( selectedPort );
        listener();
        configureStageService();
    }
    private Javalin configureHttpServer(){
        return Javalin.create(config -> {
            config.staticFiles.add(PAGES_DIR, Location.CLASSPATH);
            ;
        });
    }
    private void configureStageService(){
        StageService stageServer = new StageService();
        stageServer.initialise();
        stageServer.start();
    }
    private void configurePlaceNameService(){
        PlaceNameService placeServer = new PlaceNameService().initialise();
        placeServer.start();
    }
    private void configureScheduleService(){
        ScheduleService scheduleService = new ScheduleService().initialise();
        scheduleService.start();
    }














    /**
     * ################################################################################################################################
     * @param stage
     */

    public void setNewStage(int stage){
        HttpResponse<JsonNode> post = Unirest.post( STAGE_SVC_URL+"/stage" )
                .header( "Content-Type", "application/json" )
                .body( new StageDO( stage ))
                .asJson();
        if( HttpStatus.OK == post.getStatus() ){
            System.out.println("Post successful");
        };

    }
    public void listener(){
        try{
            final ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory( MQ.URL );
            connection = factory.createConnection( MQ.USER, MQ.PASSWD );

            final Session session = connection.createSession( false, Session.AUTO_ACKNOWLEDGE );
            final Destination dest = session.createTopic( MQ_TOPIC_NAME ); // <-- NB: Topic, not Queue!

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
                          setNewStage(theStage);
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
