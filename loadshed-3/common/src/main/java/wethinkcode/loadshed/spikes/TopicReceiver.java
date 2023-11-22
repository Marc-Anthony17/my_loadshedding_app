package wethinkcode.loadshed.spikes;

import javax.jms.*;

import org.apache.activemq.ActiveMQConnectionFactory;
import wethinkcode.loadshed.common.mq.MQ;

/**
 * I am a small "maker" app for receiving MQ messages from the Stage Service by
 * subscribing to a Topic.
 */
public class TopicReceiver implements Runnable
{
    private static long NAP_TIME = 2000; //ms

    public static final String MQ_TOPIC_NAME = "stage";

    public static void main( String[] args ){
        final TopicReceiver app = new TopicReceiver();
        app.run();
    }

    private boolean running = true;

    private Connection connection;

    @Override
    public void run(){
        setUpMessageListener();
        while( running ){
            System.out.println( "Still doing stufff..." );
            snooze();
        }
        closeConnection();
        System.out.println( "Bye..." );
    }

    private void setUpMessageListener(){
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
                        if ("SHUTDOWN".equals(body)) {
                            connection.close();
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

    private void snooze(){
        try{
            Thread.sleep( NAP_TIME );
        }catch( InterruptedException eek ){
            // meh...
        }
    }

    private void closeConnection(){
        if( connection != null ) try{
            connection.close();
        }catch( JMSException ex ){
            // meh
        }
    }

}
