/* Copyright (c) 2017 Pivotal Software, Inc. All rights reserved. */
package com.rabbitmq.integration.tests;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.jms.admin.RMQConnectionFactory;
import com.rabbitmq.jms.client.RMQConnection;
import org.junit.Before;
import org.junit.Test;

import javax.jms.*;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.*;

/**
 *
 */
public class RequeueMessageOnListenerExceptionIT extends AbstractITQueue {
    private static final String QUEUE_NAME = "test.queue." + RequeueMessageOnListenerExceptionIT.class.getCanonicalName();
    private static final String MESSAGE = "Hello " + RequeueMessageOnListenerExceptionIT.class.getName();

    @Before public void init() throws Exception {
        Connection connection = null;
        try {
            com.rabbitmq.client.ConnectionFactory connectionFactory = new ConnectionFactory();
            connection = connectionFactory.newConnection();
            connection.createChannel().queueDelete(QUEUE_NAME);
        } finally {
            if (connection  != null) {
                connection.close();
            }
        }


    }

    @Test
    public void requeueParameterTrueRuntimeExceptionInListenerMessageShouldBeNacked() throws Exception {
        sendMessage();
        QueueConnection connection = null;
        try {
            connection = connection(RMQConnection.NO_CHANNEL_QOS);
            QueueSession queueSession = connection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = queueSession.createQueue(QUEUE_NAME);
            QueueReceiver queueReceiver = queueSession.createReceiver(queue);
            final CountDownLatch latch = new CountDownLatch(1);
            queueReceiver.setMessageListener(new MessageListener() {
                @Override
                public void onMessage(Message message) {
                    if (true) {
                        latch.countDown();
                        throw new RuntimeException("runtime exception in message listener");
                    }
                }
            });

            // another consumer can consume the message
            queueSession = connection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
            queueReceiver = queueSession.createReceiver(queue);
            Message message = queueReceiver.receive(1000L);
            assertNotNull(message);
            assertTrue(message.getJMSRedelivered());
        } finally {
            if(connection != null) {
                connection.close();
            }
        }
    }

    @Test
    public void requeueParameterTrueNoExceptionInListenerQueueShouldBeEmpty() throws Exception {
        sendMessage();
        QueueConnection connection = null;
        try {
            connection = connection(RMQConnection.NO_CHANNEL_QOS);
            QueueSession queueSession = connection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = queueSession.createQueue(QUEUE_NAME);
            QueueReceiver queueReceiver = queueSession.createReceiver(queue);
            final CountDownLatch latch = new CountDownLatch(1);
            queueReceiver.setMessageListener(new MessageListener() {
                @Override
                public void onMessage(Message message) {
                    latch.countDown();
                }
            });

            // the message has been consumed, no longer in the queue
            queueSession = connection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
            queueReceiver = queueSession.createReceiver(queue);
            Message message = queueReceiver.receive(1000L);
            assertNull(message);
        } finally {
            if(connection != null) {
                connection.close();
            }
        }
    }

    private void sendMessage() throws Exception {
        try {
            queueConn.start();
            QueueSession queueSession = queueConn.createQueueSession(false, Session.CLIENT_ACKNOWLEDGE);
            Queue queue = queueSession.createQueue(QUEUE_NAME);
            QueueSender queueSender = queueSession.createSender(queue);
            queueSender.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
            TextMessage message = queueSession.createTextMessage(MESSAGE);
            queueSender.send(message);
        } finally {
            reconnect();
        }
    }

    private QueueConnection connection(int qos) throws Exception {
        RMQConnectionFactory connectionFactory = (RMQConnectionFactory) AbstractTestConnectionFactory.getTestConnectionFactory().getConnectionFactory();
        connectionFactory.setChannelsQos(qos);
        connectionFactory.setRequeueOnMessageListenerException(true);
        QueueConnection queueConnection = connectionFactory.createQueueConnection();
        queueConnection.start();
        return queueConnection;
    }



}
