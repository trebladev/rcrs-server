package sample;

import java.io.FileWriter;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

import rescuecore2.Constants;
import rescuecore2.Timestep;
import rescuecore2.log.Logger;
import rescuecore2.score.ScoreFunction;
import rescuecore2.standard.components.StandardViewer;


import rescuecore2.worldmodel.Entity;
import rescuecore2.worldmodel.EntityID;
import rescuecore2.messages.control.KVTimestep;
import rescuecore2.worldmodel.Property;

import static rescuecore2.misc.java.JavaTools.instantiate;

/**
 * A simple viewer event recorder.
 */
public class SampleViewerEventLogger extends StandardViewer {

    private static final int PRECISION = 3;

    private String JSON_RECORD_FILE_FORMAT = "%s__%d_viewer_event_log.jlog";
    private static final String TEAM_NAME_KEY = "viewer.team-name";
    private static final String RECORDS_DIR_KEY = "records.dir";
    private ScoreFunction scoreFunction;
    private NumberFormat format;

    private String teamName;
    private String recordsDir = "./records";
    private String logFilePath;

    @Override
    protected void postConnect() {
        super.postConnect();

        scoreFunction = makeScoreFunction();
        format = NumberFormat.getInstance();
        format.setMaximumFractionDigits(PRECISION);

        Logger.info("Sample Viewer Event Recorder start ...");

        // write summery
        JSONObject jsonSummery = generateSummery();
        writeJsonFile(jsonSummery, logFilePath, false);

        // write map
        JSONObject jsonRecord = new JSONObject();
        JSONArray jsonAllEntities = generateMap();

        jsonRecord.put("Entities", jsonAllEntities);
        jsonRecord.put("TimeStep", 0);

        writeJsonFile(jsonRecord, logFilePath, true);
    }

    @Override
    protected void handleTimestep(final KVTimestep kvt) {
        super.handleTimestep(kvt);

        JSONArray jsonEntities = new JSONArray();
        for (EntityID id : kvt.getChangeSet().getChangedEntities()) {
            Entity entity = model.getEntity(id);
            Set<Property> changedProperties = kvt.getChangeSet().getChangedProperties(entity.getID());

            // Filter Entity
            JSONObject jsonEntity = entity.toJson();
            JSONObject filteredJsonEntity = new JSONObject();
            for (Property property : changedProperties) {
                String propertyName = property.getURN();
                if (jsonEntity.has(propertyName) && !jsonEntity.isNull(propertyName)) {
                    String jsonEntityProperty = jsonEntity.get(propertyName).toString();
                    filteredJsonEntity.put(propertyName, jsonEntityProperty);
                }
            }

            if (!filteredJsonEntity.isEmpty()) {
                filteredJsonEntity.put("Id", jsonEntity.get("Id"));
                jsonEntities.put(jsonEntity);
            }
        }

        JSONObject jsonInfo = generateInfo(kvt);

        JSONObject jsonRecord = new JSONObject();

        jsonRecord.put("Entities", jsonEntities);
        jsonRecord.put("Info", jsonInfo);
        jsonRecord.put("TimeStep", kvt.getTime());

        writeJsonFile(jsonRecord, logFilePath, true);
    }

    private JSONObject generateSummery() {
        String mapName = getMapName();

        this.teamName = config.getValue(TEAM_NAME_KEY, "unknown");
        recordsDir = config.getValue(RECORDS_DIR_KEY, "./records");
        String totalTime = config.getValue("kernel.timesteps");
        int channelCount = config.getIntValue("comms.channels.count") - 1;//-1 for say

        String JSON_RECORD_FILE_NAME = String.format(JSON_RECORD_FILE_FORMAT, mapName, System.currentTimeMillis());
        logFilePath = recordsDir + "/" + JSON_RECORD_FILE_NAME;

        Logger.debug("Sample Viewer: mapName: " + mapName + " teamName: " + teamName + " totalTime: " + totalTime + " channelCount:" + channelCount);

        JSONObject jsonSummery = new JSONObject();
        jsonSummery.put("TotalTime", totalTime);
        jsonSummery.put("TeamName", this.teamName);
        jsonSummery.put("channelCount", channelCount);
        jsonSummery.put("MapName", mapName);
        return jsonSummery;
    }

    private JSONObject generateInfo(final KVTimestep t) {
        JSONObject jsonInfo = new JSONObject();
        double score = scoreFunction.score(model, new Timestep(t.getTime()));

        jsonInfo.put("Score", format.format(score));
        return jsonInfo;
    }

    private JSONArray generateMap() {
        JSONArray jsonAllEntities = new JSONArray();
        for (Entity entity : model.getAllEntities()) {
            jsonAllEntities.put(entity.toJson());
        }
        return jsonAllEntities;
    }

    private void writeJsonFile(JSONObject output, String filename, boolean append) {

        //Write JSON file
        try (FileWriter file = new FileWriter(filename, append)) {

            file.write(JSONObject.valueToString(output) + "\r\n");
            file.flush();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private ScoreFunction makeScoreFunction() {
        String className = config.getValue(Constants.SCORE_FUNCTION_KEY);
        ScoreFunction result = instantiate(className, ScoreFunction.class);
        result.initialise(model, config);
        return result;
    }

    private String getMapName() {
        String mapDir = config.getValue("gis.map.dir").trim();
        String[] map_spl = mapDir.split("/");
        int index = map_spl.length - 1;
        String mapName = map_spl[index].trim();
        if (mapName.equals(""))
            mapName = map_spl[--index].trim();
        if (mapName.equals("map"))
            mapName = map_spl[--index].trim();


        return mapName;
    }

    @Override
    public String toString() {
        return "SampleViewerRecorder";
    }

}