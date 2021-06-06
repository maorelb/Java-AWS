package Ex1;

import java.util.ArrayList;
import java.util.List;

import Ex1.Worker.Operation;

public class WorkerDataStructure {
    String localApplicationID;
    int maxSize;
    List<String> processed;

    public WorkerDataStructure(String localApplicationID, int maxSize) {
        this.localApplicationID = localApplicationID;
        this.maxSize = maxSize;
        this.processed = new ArrayList<>();
    }

    public String getLocalApplicationID() {
        return localApplicationID;
    }

    public int getMaxSize() {
        return maxSize;
    }

    public int getSize() {
        return processed.size();
    }

    public List<String> getProcessed() {
        return processed;
    }

    public boolean addProcessedString(String processedString) {
        if(!isFull())
            processed.add(processedString);
        return isFull();
    }

    public boolean isFull() {
        return processed.size() == maxSize;
    }
}
