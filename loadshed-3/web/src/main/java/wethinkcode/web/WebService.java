package wethinkcode.web;

import com.google.common.annotations.VisibleForTesting;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import kong.unirest.HttpResponse;
import kong.unirest.HttpStatus;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import wethinkcode.AlertService;
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
public class WebService implements Runnable
{
    public static final String MQ_URL = "tcp://localhost:61616";
    private Session session;
    public static final String MQ_USER = "admin";

    public static final String MQ_PASSWD = "admin";

    public static final int DEFAULT_PORT = 8080;

    public static final String MQ_TOPIC_NAME = "stage";
    public static final String STAGE_SVC_URL = "http://localhost:" + StageService.DEFAULT_PORT;

    public static final String PLACES_SVC_URL = "http://localhost:" + PlaceNameService.DEFAULT_PORT;

    public static final String SCHEDULE_SVC_URL = "http://localhost:" + ScheduleService.DEFAULT_PORT;

    private static final Logger SERVER_LOG = Logger.getLogger( "loadshed.stage.server" );
    private static final Logger DB_LOG = Logger.getLogger( "loadshed.stage.database" );
    private static final Logger USR_LOG = Logger.getLogger( "loadshed.stage.users" );
    private static final String PAGES_DIR = "/templates";

    public static void main( String[] args ){
        final WebService svc = new WebService().initialise();
        new Thread(svc).start();
//        svc.start();
    }
    private int stage;

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
        try {
            checker();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void launchAllServers(){
        configurePlaceNameService();
        configureScheduleService();
        server.start( DEFAULT_PORT );
        listener();
        configureStageService();
        new AlertService().run();
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
                          stage = theStage;
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
            USR_LOG.log(Level.FATAL,"NOT RUNNING THE LISTENER ", erk);

            System.out.println("brocker down");        }
    }

    public boolean isPlaceNameServiceLive(){
        try {
            Unirest.get( STAGE_SVC_URL + "/stage" ).asJson();
            return true;
        }
        catch (Exception e){
            SERVER_LOG.log(Level.FATAL,"PlaceNameService is down: ", e);
            return false;
        }
    }

    public boolean isScheduleServiceLive(){
        try {
            Unirest.get( SCHEDULE_SVC_URL + "/Gauteng/nigel" ).asJson();
            return true;
        }
        catch (Exception e){
            SERVER_LOG.log(Level.FATAL,"ScheduleService is down: ", e);

            return false;
        }
    }

    public void checker() throws InterruptedException {
        while (true){
            if (!isScheduleServiceLive()){
                setUpMessageSender("schedule_service_down");
                break;
            } else if (!isPlaceNameServiceLive()) {
                setUpMessageSender("place_name_service_down");
                break;
            }
//            System.out.println("Just checked");
            Thread.sleep(5000);
        }
    }

    private void setUpMessageSender(String alert){
        try{
            final ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory( MQ_URL );
            connection = factory.createConnection( MQ_USER, MQ_PASSWD );
            connection.start();

            session = connection.createSession( false, Session.AUTO_ACKNOWLEDGE );
            sendMessage( alert );

        }catch( JMSException erk ){
            throw new RuntimeException( erk );
        }finally{
            closeResources();
        }
    }
    private void sendMessage(String message) throws JMSException {
        javax.jms.Queue queue = session.createQueue("alert");
        javax.jms.MessageProducer producer = session.createProducer(queue);
        javax.jms.TextMessage textMessage = session.createTextMessage(message);
        producer.send(textMessage);
        System.out.println("Alert message: " + message);
        producer.close();
    }

    private void closeResources(){
        try{
            if( session != null ) session.close();
            if( connection != null ) connection.close();
        }catch( JMSException ex ){
            // wut?
        }
        session = null;
        connection = null;
    }
}
