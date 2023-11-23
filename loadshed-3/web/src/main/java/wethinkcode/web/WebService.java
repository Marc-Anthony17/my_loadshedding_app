package wethinkcode.web;

import com.google.common.annotations.VisibleForTesting;
import io.javalin.Javalin;
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

    private static final String PAGES_DIR = "/html";

    public static void main( String[] args ){
        final WebService svc = new WebService().initialise();
        svc.start();
    }

    private Javalin server;

    private int servicePort;

    private Connection connection;

    @VisibleForTesting
    WebService initialise(){
        // FIXME: Initialise HTTP client, MQ machinery and server from here
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

    private void configureHttpClient(){
        throw new UnsupportedOperationException( "TODO" );
    }

    private Javalin configureHttpServer(){
        throw new UnsupportedOperationException( "TODO" );
    }














    /**
     * ################################################################################################################################
     * @param stage
     */

    public void setNewStage(int stage){
        HttpResponse<JsonNode> post = Unirest.post( "http://localhost:"+StageService.DEFAULT_PORT+"/stage" )
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
                         int theStage = Integer.parseInt(body);
                         if ("SHUTDOWN".equals(body)) {
                             connection.close();
                         }
                         if(theStage >= 0 && theStage <9){
                          setNewStage(theStage);
                         }
                         System.out.println("Received message: " + body);
                     }catch (JMSException e) {
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
