package wethinkcode.loadshed.spikes;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Session;

import org.apache.activemq.ActiveMQConnectionFactory;

/**
 * I am a small "maker" app for receiving MQ messages from the Stage Service.
 */
public class QueueSender implements Runnable
{
    private static long NAP_TIME = 2000; //ms

    public static final String MQ_URL = "tcp://localhost:61616";

    public static final String MQ_USER = "admin";

    public static final String MQ_PASSWD = "admin";

    public static final String MQ_QUEUE_NAME = "stage";

    public static void main( String[] args ){
        final QueueSender app = new QueueSender();
        app.run();
    }

    private String[] cmdLineMsgs;

    private Connection connection;

    private Session session;

    @Override
    public void run(){
        try{
            final ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory( MQ_URL );
            connection = factory.createConnection( MQ_USER, MQ_PASSWD );
            connection.start();

            session = connection.createSession( false, Session.AUTO_ACKNOWLEDGE );
            sendAllMessages( cmdLineMsgs == null
                ? new String[]{ "{ \"stage\":17 }" }
                : cmdLineMsgs );

        }catch( JMSException erk ){
            throw new RuntimeException( erk );
        }finally{
            closeResources();
        }
        System.out.println( "Bye..." );
    }

    private void sendAllMessages(String[] messages) throws JMSException {
        javax.jms.Queue queue = session.createQueue(MQ_QUEUE_NAME);
        javax.jms.MessageProducer producer = session.createProducer(queue);

        for (String message : messages) {
            javax.jms.TextMessage textMessage = session.createTextMessage(message);
            producer.send(textMessage);
            System.out.println("Sent message: " + message);
        }

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
