package tasks;

import constants.Required;
import io.mangoo.annotations.Run;
import jakarta.inject.Inject;
import services.DataService;

import java.util.Objects;

public class ActionTask {
    private final DataService dataService;

    @Inject
    public ActionTask(DataService dataService) {
        this.dataService = Objects.requireNonNull(dataService, Required.DATA_SERVICE);
    }

    @Run(at = "Every 60m")
    public void execute() {
        dataService.cleanActions();
    }
}