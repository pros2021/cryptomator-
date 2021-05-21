package org.cryptomator.ui.health;

import com.google.common.base.Preconditions;
import com.tobiasdiez.easybind.EasyBind;
import dagger.Lazy;
import org.cryptomator.ui.common.FxController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javafx.beans.binding.Binding;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ListView;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

@HealthCheckScoped
public class CheckListController implements FxController {

	private static final Logger LOG = LoggerFactory.getLogger(CheckListController.class);
	private static final Set<Worker.State> END_STATES = Set.of(Worker.State.FAILED, Worker.State.CANCELLED, Worker.State.SUCCEEDED);

	private final Stage window;
	private final ObservableList<HealthCheckTask> tasks;
	private final ReportWriter reportWriter;
	private final ExecutorService executorService;
	private final ObjectProperty<HealthCheckTask> selectedTask;
	private final SimpleObjectProperty<Worker<?>> runningTask;
	private final Binding<Boolean> running;
	private final Binding<Boolean> finished;
	private final Map<HealthCheckTask, BooleanProperty> listPickIndicators;
	private final IntegerProperty numberOfPickedChecks;
	private final BooleanBinding anyCheckSelected;
	private final BooleanProperty showResultScreen;

	/* FXML */
	public CheckBox selectAllBox;
	public ListView<HealthCheckTask> checksListView;


	@Inject
	public CheckListController(@HealthCheckWindow Stage window, Lazy<Collection<HealthCheckTask>> tasks, ReportWriter reportWriteTask, ObjectProperty<HealthCheckTask> selectedTask, ExecutorService executorService) {
		this.window = window;
		this.tasks = FXCollections.observableArrayList(tasks.get());
		this.reportWriter = reportWriteTask;
		this.executorService = executorService;
		this.selectedTask = selectedTask;
		this.runningTask = new SimpleObjectProperty<>();
		this.running = EasyBind.wrapNullable(runningTask).mapObservable(Worker::runningProperty).orElse(false);
		this.finished = EasyBind.wrapNullable(runningTask).mapObservable(Worker::stateProperty).map(END_STATES::contains).orElse(false);
		this.listPickIndicators = new HashMap<>();
		this.numberOfPickedChecks = new SimpleIntegerProperty(0);
		this.tasks.forEach(task -> {
			var entrySelectedProp = new SimpleBooleanProperty(false);
			entrySelectedProp.addListener((observable, oldValue, newValue) -> numberOfPickedChecks.set(numberOfPickedChecks.get() + (newValue ? 1 : -1)));
			listPickIndicators.put(task, entrySelectedProp);
		});
		this.anyCheckSelected = selectedTask.isNotNull();
		this.showResultScreen = new SimpleBooleanProperty(false);
	}

	@FXML
	public void initialize() {
		checksListView.setItems(tasks);
		checksListView.setCellFactory(CheckBoxListCell.forListView(listPickIndicators::get, new StringConverter<HealthCheckTask>() {
			@Override
			public String toString(HealthCheckTask object) {
				return object.getCheck().identifier();
			}

			@Override
			public HealthCheckTask fromString(String string) {
				return null;
			}
		}));
		selectedTask.bind(checksListView.getSelectionModel().selectedItemProperty());
		selectAllBox.selectedProperty().addListener(this::selectOrDeselectAll);
		selectAllBox.visibleProperty().bind(showResultScreen.not());
		selectAllBox.managedProperty().bind(showResultScreen.not());
	}

	public void selectOrDeselectAll(ObservableValue<? extends Boolean> observable, boolean oldValue, boolean newValue) {
		listPickIndicators.forEach( (task, pickProperty) -> pickProperty.set(newValue));
	}

	@FXML
	public void runSelectedChecks() {
		Preconditions.checkState(runningTask.get() == null);
		var batch = checksListView.getItems().filtered(item -> listPickIndicators.get(item).get());
		var batchService = new BatchService(batch);
		batchService.setExecutor(executorService);
		batchService.start();
		runningTask.set(batchService);
		checksListView.setCellFactory(view -> new CheckListCell());
		showResultScreen.set(true);
		checksListView.getSelectionModel().select(batch.get(0));
		window.sizeToScene();
	}

	@FXML
	public synchronized void cancelCheck() {
		Preconditions.checkState(runningTask.get() != null);
		runningTask.get().cancel();
	}

	@FXML
	public void exportResults() {
		try {
			reportWriter.writeReport(tasks);
		} catch (IOException e) {
			//TODO: better error handling
			LOG.error("Failed to write health check report.", e);
		}
	}

	/* Getter/Setter */
	public boolean isRunning() {
		return running.getValue();
	}

	public Binding<Boolean> runningProperty() {
		return running;
	}

	public boolean isFinished() {
		return finished.getValue();
	}

	public Binding<Boolean> finishedProperty() {
		return finished;
	}

	public boolean isAnyCheckSelected() {
		return anyCheckSelected.get();
	}

	public BooleanBinding anyCheckSelectedProperty() {
		return anyCheckSelected;
	}

	public boolean getShowResultScreen() {
		return showResultScreen.get();
	}

	public BooleanProperty showResultScreenProperty() {
		return showResultScreen;
	}

	public int getNumberOfPickedChecks() {
		return numberOfPickedChecks.get();
	}

	public IntegerProperty numberOfPickedChecksProperty() {
		return numberOfPickedChecks;
	}


}
