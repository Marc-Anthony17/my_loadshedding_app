package wethinkcode.stage;


import java.util.concurrent.SynchronousQueue;

import javax.jms.*;

import com.google.gson.Gson;
import kong.unirest.HttpResponse;
import kong.unirest.HttpStatus;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import kong.unirest.json.JSONException;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.junit.jupiter.api.*;
import wethinkcode.loadshed.common.mq.MQ;
import wethinkcode.loadshed.common.transfer.StageDO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * I test StageService message sending.
 */
@Disabled
@Tag( "expensive" )
public class StageServiceMQTest
{
    public static final int TEST_PORT = 7777;

    private static StageService server;

    private static ActiveMQConnectionFactory factory;

    private static Connection mqConnection;

    final SynchronousQueue<StageDO> resultCatcher = new SynchronousQueue<>();

    @BeforeAll
    public static void startInfrastructure() throws Exception {
//        startActiveMQ();
        startMsgQueue();
        startStageSvc();
    }

    @AfterAll
    public static void cleanup() throws JMSException {
        server.stop();
//        mqConnection.close();
    }

//    @BeforeEach
//    public void connectMqListener( MessageListener listener ) throws JMSException {
//        mqConnection = factory.createConnection();
//        final Session session = mqConnection.createSession( false, Session.AUTO_ACKNOWLEDGE );
//        final Destination dest = session.createTopic( StageService.MQ_TOPIC_NAME );
//
//        final MessageConsumer receiver = session.createConsumer( dest );
//        receiver.setMessageListener( listener );
//
//        mqConnection.start();
//    }

    public static void startActiveMQ() throws Exception {
        BrokerService broker = new BrokerService();
        broker.setPersistent(false);
        broker.setUseJmx(false);
        broker.addConnector("tcp://localhost:61616");
        broker.start();
        broker.waitUntilStarted();
    }

    @AfterEach
    public void closeMqConnection() throws JMSException {
        mqConnection.close();
        mqConnection = null;
    }

    @Test
    public void sendMqEventWhenStageChanges() throws InterruptedException {
        setUpMessageListener();
        final HttpResponse<StageDO> startStage = Unirest.get( serverUrl() + "/stage" ).asObject( StageDO.class );
        assertEquals( HttpStatus.OK, startStage.getStatus() );

        final StageDO data = startStage.getBody();
        final int newStage = data.getStage() + 1;

        final HttpResponse<JsonNode> changeStage = Unirest.post( serverUrl() + "/stage" )
            .header( "Content-Type", "application/json" )
            .body( new StageDO( newStage ))
            .asJson();
        assertEquals( HttpStatus.OK, changeStage.getStatus() );
        final int stage = getStageFromResponse( changeStage );
        assertEquals( stage , resultCatcher.take().getStage() );

//        fail( "TODO" );


    }

    private static void startMsgQueue() throws JMSException {
        factory = new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false");
    }

    private static void startStageSvc(){
        server = new StageService().initialise();
        server.start( TEST_PORT );
    }

    private String serverUrl(){
        return "http://localhost:" + TEST_PORT;
    }
    private void setUpMessageListener(){
        try{
            final ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory( MQ.URL );
            mqConnection = factory.createConnection( MQ.USER, MQ.PASSWD );

            final Session session = mqConnection.createSession( false, Session.AUTO_ACKNOWLEDGE );
            final Destination dest = session.createTopic( "stage" ); // <-- NB: Topic, not Queue!

            final MessageConsumer receiver = session.createConsumer( dest );
            receiver.setMessageListener( new MessageListener(){
             @Override
             public void onMessage( Message m ){
                 try {
                     String body = ((TextMessage) m).getText();
                     if ("SHUTDOWN".equals(body)) {
                         mqConnection.close();
                     }
                     Gson gson = new Gson();
                     StageDO stageData = gson.fromJson(body, StageDO.class);

                     resultCatcher.put(stageData);
                 }catch (JMSException | InterruptedException e) {
                     throw new RuntimeException(e);
                 }
             }
         }
            );
            mqConnection.start();

        }catch( JMSException erk ){
            throw new RuntimeException( erk );
        }
    }
    private static int getStageFromResponse( HttpResponse<JsonNode> response ) throws JSONException {
        return response.getBody().getObject().getInt( "stage" );
    }
}
