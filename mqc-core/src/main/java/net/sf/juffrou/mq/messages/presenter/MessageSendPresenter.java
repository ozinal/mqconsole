package net.sf.juffrou.mq.messages.presenter;

import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.Accordion;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableColumn.CellEditEvent;
import javafx.scene.control.TablePosition;
import javafx.scene.control.TableView;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Callback;
import net.sf.juffrou.mq.dom.HeaderDescriptor;
import net.sf.juffrou.mq.dom.MessageDescriptor;
import net.sf.juffrou.mq.dom.QueueDescriptor;
import net.sf.juffrou.mq.error.MissingReplyQueueException;
import net.sf.juffrou.mq.messages.MessageSendController;
import net.sf.juffrou.mq.ui.ExceptionDialog;
import net.sf.juffrou.mq.ui.XmlViewer;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class MessageSendPresenter implements DisposableBean {

	protected static final Logger LOG = LoggerFactory.getLogger(MessageSendPresenter.class);
	
	private static final String SCRIPT_SET_TEXT_PREFIX = "editor.setValue(\"";
	private static final String SCRIPT_PASTE_TEXT_PREFIX = "editor.onPaste(\"";
	private static final String SCRIPT_SET_TEXT_SUFFIX = "\");";
	private static final String SCRIPT_GET_TEXT = "editor.getValue();";

	@FXML
	private Accordion messageAccordionSend;

	@FXML
	private Accordion messageAccordionReceive;

	@FXML
	private TitledPane sendHeadersPane;

	@FXML
	private TitledPane sendPayloadPane;

	@FXML
	private TitledPane receivePayloadPane;

	@FXML
	private TableView<HeaderDescriptor> sendHeadersTable;
	
	@FXML
	private TableColumn<HeaderDescriptor, String> sendHeadersTableName;

	@FXML
	private TableColumn<HeaderDescriptor, Object> sendHeadersTableValue;

	@FXML
	private TableView<HeaderDescriptor> receiveHeadersTable;

	@FXML
	private AnchorPane requestPayloadAnchorPane;

	@FXML
	private AnchorPane responsePayloadAnchorPane;
	
	@FXML
	private CheckBox hasReply;

	@FXML
	private ComboBox<QueueDescriptor> replyQueueCB;

	@FXML
	private TabPane messageTabs;

	@FXML
	private Tab responseTab;

	@Value("${broker_timeout}")
	private Integer brokerTimeout;

	private QueueDescriptor queueNameSend;
	
	private ExecutorService executor = null; // Executor service for XML Viewer
	private XmlViewer requestXmlViewer;
	private XmlViewer responseXmlViewer;
	
	@Autowired
	private MessageSendController messageSendController;

	public QueueDescriptor getQueueSend() {
		return queueNameSend;
	}

	public void setQueueSend(QueueDescriptor queueSend) {
		this.queueNameSend = queueSend;
	}

	public MessageDescriptor getSendMessage() {
		MessageDescriptor messageDescriptor = new MessageDescriptor();

		String text = requestXmlViewer.getText();
		messageDescriptor.setText(text);

		ObservableList<HeaderDescriptor> rows = sendHeadersTable.getItems();
		if (rows != null) {
			Iterator<HeaderDescriptor> iterator = rows.iterator();
			while (iterator.hasNext())
				messageDescriptor.addHeader(iterator.next());
		}
		return messageDescriptor;
	}

	public void setSentMessage(MessageDescriptor messageDescriptor) {
//		sendPayload.setText(messageDescriptor.getText());
		
		ObservableList<HeaderDescriptor> rows = FXCollections.observableArrayList();
		rows.addAll(messageDescriptor.getHeaders());
		sendHeadersTable.getItems().clear();
		sendHeadersTable.setItems(rows);
		sendHeadersPane.setExpanded(true);
	}

	public void setReceiveMessage(MessageDescriptor messageDescriptor) {
		if(messageDescriptor.getText() != null && !messageDescriptor.getText().isEmpty()) {
			
			responseXmlViewer.replaceText(messageDescriptor.getText());
		}

		ObservableList<HeaderDescriptor> rows = FXCollections.observableArrayList();
		rows.addAll(messageDescriptor.getHeaders());
		receiveHeadersTable.getItems().clear();
		receiveHeadersTable.setItems(rows);
		receivePayloadPane.setExpanded(true);
	}

	public void setQueueDescriptors(ObservableList<QueueDescriptor> queues) {
		replyQueueCB.setItems(queues);
	}

	public void initialize() {
		sendHeadersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
		sendHeadersTableName.setOnEditCommit(
				new EventHandler<CellEditEvent<HeaderDescriptor, String>>() {
	                public void handle(CellEditEvent<HeaderDescriptor, String> t) {
	                    ((HeaderDescriptor) t.getTableView().getItems().get(
	                        t.getTablePosition().getRow())
	                        ).setName(t.getNewValue());
	                }
	            }
	        );
		sendHeadersTableValue.setOnEditCommit(
				new EventHandler<CellEditEvent<HeaderDescriptor, Object>>() {
	                public void handle(CellEditEvent<HeaderDescriptor, Object> t) {
	                    ((HeaderDescriptor) t.getTableView().getItems().get(
	                        t.getTablePosition().getRow())
	                        ).setValue(t.getNewValue());
	                }
	            }
	        );
		receiveHeadersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
		final Callback<ListView<QueueDescriptor>, ListCell<QueueDescriptor>> factory = new Callback<ListView<QueueDescriptor>, ListCell<QueueDescriptor>>() {
			@Override
			public ListCell<QueueDescriptor> call(ListView<QueueDescriptor> arg0) {
				return new QueueDescriptorCell();
			}

		};
		replyQueueCB.setCellFactory(factory);
		replyQueueCB.setButtonCell(new QueueDescriptorCell());
		sendPayloadPane.setExpanded(true);
		
		// prevent all panes inside the accordion to collapse
		messageAccordionSend.expandedPaneProperty().addListener(new ChangeListener<TitledPane>() {
	        @Override public void changed(ObservableValue<? extends TitledPane> property, final TitledPane oldPane, final TitledPane newPane) {
	          if (oldPane != null) oldPane.setCollapsible(true);
	          if (newPane != null) Platform.runLater(new Runnable() { @Override public void run() { 
	            newPane.setCollapsible(false); 
	          }});
	        }
	      });

		messageAccordionSend.setExpandedPane(sendPayloadPane);

		// prevent all panes inside the accordion to collapse
		messageAccordionReceive.expandedPaneProperty().addListener(new ChangeListener<TitledPane>() {
	        @Override public void changed(ObservableValue<? extends TitledPane> property, final TitledPane oldPane, final TitledPane newPane) {
	          if (oldPane != null) oldPane.setCollapsible(true);
	          if (newPane != null) Platform.runLater(() -> newPane.setCollapsible(false));
	        }
	      });

		messageAccordionReceive.setExpandedPane(receivePayloadPane);
		
		if(executor == null)
			executor = Executors.newSingleThreadExecutor();

		// Prepare the request payload
		requestXmlViewer = new XmlViewer(executor);
		
		requestPayloadAnchorPane.getStylesheets().add(XmlViewer.class.getResource("xml-highlighting.css").toExternalForm());
		requestPayloadAnchorPane.getChildren().add(requestXmlViewer);
		requestPayloadAnchorPane.setTopAnchor(requestXmlViewer, 0.0);
		requestPayloadAnchorPane.setBottomAnchor(requestXmlViewer, 0.0);
		requestPayloadAnchorPane.setLeftAnchor(requestXmlViewer, 0.0);
		requestPayloadAnchorPane.setRightAnchor(requestXmlViewer, 0.0);
		
		// Prepare the response payload
		responseXmlViewer = new XmlViewer(executor);
		responseXmlViewer.setEditable(false);
		
		responsePayloadAnchorPane.getStylesheets().add(XmlViewer.class.getResource("xml-highlighting.css").toExternalForm());
		responsePayloadAnchorPane.getChildren().add(responseXmlViewer);
		responsePayloadAnchorPane.setTopAnchor(responseXmlViewer, 0.0);
		responsePayloadAnchorPane.setBottomAnchor(responseXmlViewer, 0.0);
		responsePayloadAnchorPane.setLeftAnchor(responseXmlViewer, 0.0);
		responsePayloadAnchorPane.setRightAnchor(responseXmlViewer, 0.0);
		
		
		// set the disposable bean
		/*
		responsePayloadAnchorPane.getScene().getWindow().setOnCloseRequest( new EventHandler<WindowEvent>() {

			@Override
			public void handle(WindowEvent event) {
				try {
					destroy();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
		*/
	}

	private static class QueueDescriptorCell extends ListCell<QueueDescriptor> {
		@Override
		protected void updateItem(QueueDescriptor item, boolean empty) {
			super.updateItem(item, empty);
			if (item != null)
				setText(item.getName());
		}
	}
	
	@FXML
	private void addHeader(ActionEvent event) {
		// user selected Add Row from the context menu on the send headers table
		
		HeaderDescriptor hd = new HeaderDescriptor();
		hd.setName("header_name (double click to change)");
		hd.setValue("value (double click to change)");
		ObservableList<HeaderDescriptor> items = sendHeadersTable.getItems();
		if(items != null)
			items.add(hd);
		else {
			items = FXCollections.observableArrayList();
			items.add(hd);
			sendHeadersTable.setItems(items);
		}
	}

	@FXML
	private void removeHeader(ActionEvent event) {
		
		ObservableList<TablePosition> cells = sendHeadersTable.getSelectionModel().getSelectedCells();
		int row = cells.get(0).getRow();
		sendHeadersTable.getItems().remove(row);
	}

	@FXML
	private void hasReplyChanged(ActionEvent actionEvent) {
		
		if(hasReply.isSelected()) {
			replyQueueCB.setDisable(false);
			responseTab.setDisable(false);
		}
		else {
			replyQueueCB.setDisable(true);
			responseTab.setDisable(true);
		}
	}

	
	@FXML
	private void sendButton(ActionEvent actionEvent) {
		QueueDescriptor queue = replyQueueCB.getValue();
		MessageDescriptor messageDescriptor = getSendMessage();

		try {
			messageSendController.sendMessage(this, messageDescriptor, queueNameSend, hasReply.isSelected(), queue);
		} catch (MissingReplyQueueException e) {
			
			Alert alert = new Alert(AlertType.INFORMATION);
			alert.setTitle("Information");
			alert.setHeaderText("MQConsole Message");
			alert.setContentText(e.getMessage());
			alert.showAndWait();
			
		} catch(Exception e) {

			ExceptionDialog alert = new ExceptionDialog();
			alert.setTitle("Error Dialog");
			alert.setHeaderText("MQConsole Message");
			alert.setContentText(e.getMessage());
			alert.setException(e);
			alert.showAndWait();

		}
	}


	public void displayMessageReceived(MessageDescriptor messageDescriptor) {
		setReceiveMessage(messageDescriptor);
		receivePayloadPane.setExpanded(true);
		messageTabs.getSelectionModel().clearAndSelect(1);
	}
	
	public Stage getStage() {
		return (Stage) messageAccordionSend.getScene().getWindow();
	}

	@Override
	public void destroy() throws Exception {
		executor.shutdownNow();
		executor = null;
	}
	
}
