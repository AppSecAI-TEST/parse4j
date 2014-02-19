package org.parse4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONException;
import org.json.JSONObject;
import org.parse4j.callback.DeleteCallback;
import org.parse4j.callback.SaveCallback;
import org.parse4j.command.ParseCommand;
import org.parse4j.command.ParseDeleteCommand;
import org.parse4j.command.ParsePostCommand;
import org.parse4j.command.ParsePutCommand;
import org.parse4j.command.ParseResponse;
import org.parse4j.operation.DeleteFieldOperation;
import org.parse4j.operation.IncrementFieldOperation;
import org.parse4j.operation.ParseFieldOperation;
import org.parse4j.operation.SetFieldOperation;

public class ParseObject {

	private String objectId;
	private String className;
	private String endPoint;
	private boolean isDirty = false;
	
	private Map<String, Object> data;
	private Map<String, ParseFieldOperation> operations;
	private List<String> dirtyKeys;

	private Date updatedAt;
	private Date createdAt;

	protected ParseObject() {
		
	}
	
	public ParseObject(String className) {
		this.className = className;
		this.data = new Hashtable<String, Object>();
		this.operations = new Hashtable<String, ParseFieldOperation>();
		this.dirtyKeys = new ArrayList<String>();
		setEndPoint("classes/" + className);
	}

	public static ParseObject create(String className) {
		return new ParseObject(className);
	}

	public String getObjectId() {
		return this.objectId;
	}

	public Date getUpdatedAt() {
		return this.updatedAt;
	}

	public Date getCreatedAt() {
		return this.createdAt;
	}

	public String getClassName() {
		return this.className;
	}

	public Set<String> keySet() {
		return Collections.unmodifiableSet(this.data.keySet());
	}

	public Date getDate(String key) {

		// checkGetAccess(key);

		if (!this.data.containsKey(key)) {
			return null;
		}
		Object value = this.data.get(key);
		if (!(value instanceof Date)) {
			return null;
		}
		return (Date) value;
	}

	public boolean getBoolean(String key) {

		// checkGetAccess(key);

		if (!this.data.containsKey(key)) {
			return false;
		}
		Object value = this.data.get(key);
		if (!(value instanceof Boolean)) {
			return false;
		}
		return ((Boolean) value).booleanValue();
	}

	public Number getNumber(String key) {

		// checkGetAccess(key);

		if (!this.data.containsKey(key)) {
			return null;
		}
		Object value = this.data.get(key);
		if (!(value instanceof Number)) {
			return null;
		}
		return (Number) value;
	}

	public int getInt(String key) {
		Number number = getNumber(key);
		if (number == null) {
			return 0;
		}
		return number.intValue();
	}

	public double getDouble(String key) {
		Number number = getNumber(key);
		if (number == null) {
			return 0.0D;
		}
		return number.doubleValue();
	}

	public long getLong(String key) {
		Number number = getNumber(key);
		if (number == null) {
			return 0L;
		}
		return number.longValue();
	}

	public String getString(String key) {

		// checkGetAccess(key);

		if (!this.data.containsKey(key)) {
			return null;
		}
		Object value = this.data.get(key);
		if (!(value instanceof String)) {
			return null;
		}
		return (String) value;
	}

	public void clearData() {
		data.clear();
		this.dirtyKeys.clear();
		operations.clear();
		isDirty = false;
		objectId = null;
		createdAt = null;
		updatedAt = null;
	}

	public boolean has(String key) {
		return containsKey(key);
	}

	public boolean containsKey(String key) {
		return this.data.containsKey(key);
	}

	public boolean hasSameId(ParseObject other) {
		return (getClassName() != null) && (getObjectId() != null)
				&& (getClassName().equals(other.getClassName()))
				&& (getObjectId().equals(other.getObjectId()));
	}

	public void put(String key, Object value) {
		
		if (key == null) {
			throw new IllegalArgumentException("key may not be null.");
		}

		if (value == null) {
			throw new IllegalArgumentException("value may not be null.");
		}
		
		if (value instanceof ParseFile && !((ParseFile) value).isUploaded()) {
			throw new IllegalArgumentException(
					"ParseFile must be saved before being set on a ParseObject.");
		}
		
		if(Parse.isInvalidKey(key)) {
			throw new IllegalArgumentException("reserved value for key: "
					+ key);			
		}
		
		if (!Parse.isValidType(value)) {
			throw new IllegalArgumentException("invalid type for value: "
					+ value.getClass().toString());
		}

		performOperation(key, new SetFieldOperation(value));
		
	}
	
	private void performOperation(String key, ParseFieldOperation operation) {
		
		//if field already exist, remove field and any pending operation for that field
		if(has(key)) {
			operations.remove(key);
			data.remove(key);
		}
		
		Object value = operation.apply(null, this, key);
		data.put(key, value);
		operations.put(key, operation);
		dirtyKeys.add(key);
		isDirty = true;
		
	}
	
	public void remove(String key) {
		
		if(has(key)) {
			if(objectId != null) {
				//if the object was saved before, we need to add the delete operation
				operations.put(key, new DeleteFieldOperation());
			}
			else {
				operations.remove(key);
			}
			data.remove(key);
			dirtyKeys.add(key);
			isDirty = true;
		}
		
	}
	
	public void decrement(String key) {
		increment(key, Integer.valueOf(-1));
	}
	
	public void increment(String key) {
		increment(key, Integer.valueOf(1));
	}
	
	public void increment(String key, Number amount) {
		
		IncrementFieldOperation operation = new IncrementFieldOperation(amount);
		Object oldValue = data.get(key);
		Object newValue = operation.apply(oldValue, this, key);
		data.put(key, newValue);
		operations.put(key, operation);
		dirtyKeys.add(key);
		isDirty = true;		
		
	}
	
	public void save() throws ParseException {

		if(!isDirty) return;

		ParseCommand command;
		if(objectId == null) {
			command = new ParsePostCommand(getEndPoint());
		}
		else {
			command =  new ParsePutCommand(getEndPoint(), getObjectId());
		}

		System.out.println("parseData-> " + getParseData());
		command.setData(getParseData());
		ParseResponse response = command.perform();
		if(!response.isFailed()) {
			JSONObject jsonResponse = response.getJsonObject();
			if (jsonResponse == null) {
				throw response.getException();
			}
			try {
				if(getObjectId() == null) {
					setObjectId(jsonResponse.getString(ParseConstants.FIELD_OBJECT_ID));
					String createdAt = jsonResponse.getString(ParseConstants.FIELD_CREATED_AT);
					setCreatedAt(Parse.parseDate(createdAt));
					setUpdatedAt(Parse.parseDate(createdAt));
				}
				else {
					String updatedAt = jsonResponse.getString(ParseConstants.FIELD_UPDATED_AT);
					setUpdatedAt(Parse.parseDate(updatedAt));
				}
				
				this.isDirty = false;
				this.operations.clear();
				this.dirtyKeys.clear();
			} 
			catch (JSONException e) {
				throw new ParseException(
						ParseException.INVALID_JSON,
						"Although Parse reports object successfully saved, the response was invalid.",
						e);
			}			
		}
		else {
			throw response.getException();
		}
	}
	
	public void delete() throws ParseException {
		
		if(objectId == null) return;
		
		ParseCommand command = new ParseDeleteCommand(getEndPoint(), getObjectId());
		ParseResponse response = command.perform();
		if(response.isFailed()) {
			throw response.getException();
		}
		
		this.updatedAt = null;
		this.createdAt = null;
		this.objectId = null;
		this.isDirty = false;
		this.operations.clear();
		this.dirtyKeys.clear();
	}	
	
	public JSONObject getParseData() {
		JSONObject parseData = new JSONObject();
		
		for(String key : operations.keySet()) {
			ParseFieldOperation operation = (ParseFieldOperation) operations.get(key);
			if(operation instanceof SetFieldOperation) {
				parseData.put(key, operation.encode());
			}
			else if(operation instanceof IncrementFieldOperation) {
				parseData.put(key, operation.encode());
			}
			else if(operation instanceof DeleteFieldOperation) {
				parseData.put(key, operation.encode());
			}
			else {
				//here we deal will sub objects like ParseObject;
				Object obj = data.get(key);
				if(obj instanceof ParseObject) {
					ParseObject pob = (ParseObject) obj;
					parseData.put(key, pob.getParseData());
				}
			}
			
		}
		
		return parseData;
	}
	
	public void saveInBackground() {
		saveInBackground(null);
	}
	
	public void deleteInBackground() {
		deleteInBackground(null);
	}	
	
	public void saveInBackground(SaveCallback saveCallback) {
		SaveInBackgroundThread task = new SaveInBackgroundThread(saveCallback);
		ParseExecutor.runInBackground(task);
	}
	
	public void deleteInBackground(DeleteCallback deleteCallback) {
		DeleteInBackgroundThread task = new DeleteInBackgroundThread(deleteCallback);
		ParseExecutor.runInBackground(task);
	}
		
	protected void setEndPoint(String endPoint) {
		this.endPoint = endPoint;
	}

	public void setObjectId(String objectId) {
		this.objectId = objectId;
	}

	public void setCreatedAt(Date createdAt) {
		this.createdAt = createdAt;
	}	
	
	public void setUpdatedAt(Date updatedAt) {
		this.updatedAt = updatedAt;
	}
	
	protected String getEndPoint() {
		return this.endPoint;
	}
	
	class DeleteInBackgroundThread extends Thread {
		DeleteCallback mDeleteCallback;

		public DeleteInBackgroundThread(DeleteCallback callback) {
			mDeleteCallback = callback;
		}

		public void run() {
			ParseException exception = null;
			try {
				delete();
			} catch (ParseException e) {
				exception = e;
			}
			if (mDeleteCallback != null) {
				mDeleteCallback.done(exception);
			}
		}
	}
	
	class SaveInBackgroundThread extends Thread {
		SaveCallback mSaveCallback;

		public SaveInBackgroundThread(SaveCallback callback) {
			mSaveCallback = callback;
		}

		public void run() {
			System.out.println("SaveInBackgroundThread.run()");
			ParseException exception = null;
			try {
				save();
			} catch (ParseException e) {
				exception = e;
			}
			if (mSaveCallback != null) {
				mSaveCallback.done(exception);
			}
		}
	}
	
}