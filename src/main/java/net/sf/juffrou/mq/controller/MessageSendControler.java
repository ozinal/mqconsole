package net.sf.juffrou.mq.controller;

import java.util.Collections;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Accordion;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.util.Callback;
import net.sf.juffrou.mq.dom.HeaderDescriptor;
import net.sf.juffrou.mq.dom.MessageDescriptor;
import net.sf.juffrou.mq.dom.QueueDescriptor;
import net.sf.juffrou.mq.dom.ReturnInfo;
import net.sf.juffrou.mq.util.MessageDescriptorHelper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.ibm.mq.MQException;
import com.ibm.mq.MQGetMessageOptions;
import com.ibm.mq.MQMessage;
import com.ibm.mq.MQPutMessageOptions;
import com.ibm.mq.MQQueue;
import com.ibm.mq.MQQueueManager;
import com.ibm.mq.constants.MQConstants;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class MessageSendControler {

	@FXML
	private Accordion messageAccordion;

	@FXML
	private TitledPane sendPayloadPane;

	@FXML
	private TitledPane receivePayloadPane;

	@FXML
	private TableView<HeaderDescriptor> sendHeadersTable;

	@FXML
	private TableView<HeaderDescriptor> receiveHeadersTable;

	@FXML
	private TextArea sendPayload;

	@FXML
	private TextArea receivePayload;

	@FXML
	private ComboBox<QueueDescriptor> replyQueueCB;

	@Autowired
	@Qualifier("mqQueueManager")
	private MQQueueManager qm;

	private String queueNameSend;

	public String getQueueNameSend() {
		return queueNameSend;
	}

	public void setQueueNameSend(String queueNameSend) {
		this.queueNameSend = queueNameSend;
	}

	public MessageDescriptor getSendMessage() {
		MessageDescriptor messageDescriptor = new MessageDescriptor();
		messageDescriptor.setText(sendPayload.getText());
		receivePayload.setText(messageDescriptor.getText());

		receiveHeadersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

		receiveHeadersTable.getItems();
		ObservableList<HeaderDescriptor> rows = receiveHeadersTable.getItems();
		messageDescriptor.getHeaders().addAll(rows);
		return messageDescriptor;
	}

	public void setReceiveMessage(MessageDescriptor messageDescriptor) {
		receivePayload.setText(messageDescriptor.getText());

		receiveHeadersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

		ObservableList<HeaderDescriptor> rows = FXCollections.observableArrayList();
		rows.addAll(messageDescriptor.getHeaders());
		receiveHeadersTable.setItems(rows);
		receivePayloadPane.setExpanded(true);
	}

	public void setQueueDescriptors(ObservableList<QueueDescriptor> queues) {
		replyQueueCB.setItems(queues);
	}

	public void initialize() {
		final Callback<ListView<QueueDescriptor>, ListCell<QueueDescriptor>> factory = new Callback<ListView<QueueDescriptor>, ListCell<QueueDescriptor>>() {
			@Override
			public ListCell<QueueDescriptor> call(ListView<QueueDescriptor> arg0) {
				return new QueueDescriptorCell();
			}

		};
		replyQueueCB.setCellFactory(factory);
		replyQueueCB.setButtonCell(new QueueDescriptorCell());
	}

	private static class QueueDescriptorCell extends ListCell<QueueDescriptor> {
		@Override
		protected void updateItem(QueueDescriptor item, boolean empty) {
			super.updateItem(item, empty);
			if (item != null)
				setText(item.getName());
		}
	}

	protected void sendButton(ActionEvent actionEvent) {
		QueueDescriptor queue = replyQueueCB.getValue();
		MessageDescriptor messageDescriptor = getSendMessage();
		sendMessage(messageDescriptor.getText(), queue.getName());
	}

	// This method called to send MQ message to the norma messaging server
	// RECEIVES a message STRING and returns a message object (used as a
	// reference for the reply)
	public void sendMessage(String messageText, String queueNameReceive) {

		MQQueue requestQueue = null;
		try {
			MQMessage sendMessage = null;

			try {
				int openOptions;

				// If the name of the request queue is the same as the reply
				// queue...
				if (queueNameSend.equals(queueNameReceive)) {
					openOptions = MQConstants.MQOO_INPUT_AS_Q_DEF | MQConstants.MQOO_OUTPUT;
				} else {
					openOptions = MQConstants.MQOO_OUTPUT; // Open queue to
															// perform MQPUTs
				}

				// Now specify the queue that we wish to open, and the open
				// options...
				requestQueue = qm.accessQueue(queueNameSend, openOptions, null, // default q manager
						null, // no dynamic q name
						null); // no alternate user id

				// Create new MQMessage object
				sendMessage = new MQMessage();
			} catch (NullPointerException e) {
				e.printStackTrace();
			}

			sendMessage.format = MQConstants.MQFMT_STRING; // Set message format
															// to
															// MQC.MQFMT_STRING
															// for use without
															// MQCIH header

			// NB. Change to 'MQCICS ' if using header !!!
			//			sendMessage.characterSet = 1208; // UTF-8

			// String str = "AMQ!NEW_SESSION_CORRELID";
			// byte byteArray[] = str.getBytes();
			// sendMessage.correlationId = byteArray;//str;

			// Set request type
			sendMessage.messageType = MQConstants.MQMT_REQUEST;
			//			sendMessage.messageType = MQConstants.MQMT_DATAGRAM;

			// Set reply queue
			sendMessage.replyToQueueName = queueNameReceive;

			// Set message text
			// String buffer = new String(bufferFront + messageText +
			// bufferEnd);
			String buffer = messageText;
			sendMessage.writeString(buffer);

			// Specify the message options...(default)
			MQPutMessageOptions pmo = new MQPutMessageOptions();
			pmo.options = MQConstants.MQPMO_ASYNC_RESPONSE | MQConstants.MQPMO_NEW_MSG_ID;

			// Put the message on the queue using default options
			try {
				requestQueue.put(sendMessage, pmo);
			} catch (NullPointerException e) {
				System.out.println("Request Q is null - cannot put message");
			}
			System.out.println("Message placed on queue");

			// Store the messageId for future use...
			// Define a MQMessage object to store the message ID as a
			// correlation ID
			// so we can retrieve the correct reply message later.
			MQMessage storedMessage = new MQMessage();

			// Copy current message ID across to the correlation ID
			storedMessage.correlationId = sendMessage.messageId;
			// storedMessage.characterSet = 1208; // UTF-8

			System.out.println("Message ID for sent message = '" + new String(sendMessage.messageId) + "'");
			System.out.println("Correlation ID stored = '" + new String(storedMessage.correlationId) + "'");

			// activate the receiving thread
			ReceivingThread responseThread = new ReceivingThread(storedMessage, queueNameReceive);
			responseThread.run();

		} catch (MQException ex) {
			System.out.println("MQCommunicator.send - MQException occurred : Completion code " + ex.completionCode
					+ " Reason code " + ex.reasonCode);
		} catch (java.io.IOException ex) {
			System.out.println("MQCommunicator.send - IOException occurred: " + ex);
		} catch (Exception ex) {
			System.out.println("MQCommunicator.send - General Exception occurred: " + ex);
		}
	}

	private class ReceivingThread implements Runnable {

		private final MQMessage replyMessage;
		private final String queueNameReceive;

		public ReceivingThread(MQMessage replyMessage, String queueNameReceive) {
			this.replyMessage = replyMessage;
			this.queueNameReceive = queueNameReceive;
		}

		// Get the reply from MVS containing account information
		public MessageDescriptor receive() {
			MQQueue replyQueue = null;
			try {
				// Construct new MQGetMessageOptions object
				MQGetMessageOptions gmo = new MQGetMessageOptions();

				// Set the get message options.. specify that we want to wait
				// for reply message
				// AND *** SET OPTION TO CONVERT CHARS TO RIGHT CHAR SET ***
				gmo.options = MQConstants.MQGMO_WAIT | MQConstants.MQGMO_CONVERT;

				gmo.options |= MQConstants.MQGMO_PROPERTIES_FORCE_MQRFH2;
				gmo.options |= MQConstants.MQGMO_CONVERT;

				// Specify the wait interval for the message in milliseconds
				gmo.waitInterval = 40000;

				System.out.println("Current Msg ID used for receive: '" + new String(replyMessage.messageId) + "'");
				System.out.println("Correlation ID to use for receive: '" + new String(replyMessage.correlationId)
						+ "'");
				System.out.println("Supported character set to use for receive: " + replyMessage.characterSet);

				// If the name of the request queue is the same as the reply
				// queue...(again...)
				int openOptions;
				if (queueNameReceive.equals(queueNameSend)) {
					openOptions = MQConstants.MQOO_INPUT_AS_Q_DEF | MQConstants.MQOO_OUTPUT; // Same options as out
																								// bound queue
				} else {
					openOptions = MQConstants.MQOO_INPUT_AS_Q_DEF; // in bound
																	// options
																	// only
				}
				// openOptions |= MQConstants.MQOO_READ_AHEAD;
				replyQueue = qm.accessQueue(queueNameReceive, openOptions, null, // default q manager
						null, // no dynamic q name
						null); // no alternate user id

				// Following test lines will cause any message on the queue to
				// be received regardless of
				// whatever message ID or correlation ID it might have
				// replyMessage.messageId = MQConstants.MQMI_NONE;
				// replyMessage.correlationId = MQConstants.MQCI_NONE;

				//				replyMessage.characterSet = 1208; // UTF-8 (will be charset=819 when the msg has Portuguese accented chars)

				replyMessage.format = MQConstants.MQFMT_RF_HEADER_2;
				// replyMessage.setBooleanProperty(MQConstants.WMQ_MQMD_READ_ENABLED,
				// true);

				// The replyMessage will have the correct correlation id for the
				// message we want to get.
				// Get the message off the queue..
				replyQueue.get(replyMessage, gmo);
				// And prove we have the message by displaying the message text
				System.out.println("The receive message character set is: " + replyMessage.characterSet);

				MessageDescriptor replyMessageDescriptor = MessageDescriptorHelper.createMessageDescriptor(replyMessage);

				return replyMessageDescriptor;

			} catch (MQException ex) {
				System.out.println("MQCommunicator.receive - MQ error occurred : Completion code " + ex.completionCode
						+ "\n>MQStatus: Reason code " + ex.reasonCode);
				ReturnInfo info = new ReturnInfo();
				info.headers = Collections.EMPTY_MAP;
				info.messageText = "MQ error occurred : Completion code " + ex.completionCode
						+ "\n>MQStatus: Reason code " + ex.reasonCode;
				return null;
			} catch (java.io.IOException ex) {
				System.out.println("MQCommunicator.receive - error occurred: " + ex);
			} catch (Exception ex) {
				System.out.println("MQCommunicator.receive - general error occurred: " + ex);
			} finally {
				if (replyQueue != null)
					try {
						replyQueue.close();
					} catch (MQException e) {
						e.printStackTrace();
					}
			}

			// Return null if unsucessful
			return null;
		}

		@Override
		public void run() {

			// get the reply
			MessageDescriptor messageDescriptor = receive();
			setReceiveMessage(messageDescriptor);
		}

	}

}
