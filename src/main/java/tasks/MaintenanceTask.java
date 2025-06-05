package tasks;

import constants.Required;
import io.mangoo.annotations.Run;
import jakarta.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import services.DataService;

import java.util.Objects;

public class MaintenanceTask {
    private static final Logger LOG = LogManager.getLogger(MaintenanceTask.class);
    private final DataService dataService;

    @Inject
    public MaintenanceTask(DataService dataService) {
        this.dataService = Objects.requireNonNull(dataService, Required.DATA_SERVICE);
    }

    @Run(at = "Every 60m")
    public void execute() {
        LOG.info("Started maintenance task");
        dataService.cleanActions();
        LOG.info("Finished maintenance task");
    }
}