package org.lockss.db;

import java.nio.ByteBuffer;

import org.bson.types.ObjectId;

import com.mongodb.DBObject;

public class MongoHelper {
	
	private MongoHelper() {}
	
	public static Long objectIdToLongId(ObjectId objectId) {
		ByteBuffer bb = ByteBuffer.wrap(objectId.toByteArray());
		return Long.valueOf(bb.getLong());
	}
	
	public static long readLong(DBObject dbObject, String fieldName) {
		if (dbObject.containsField(fieldName)) {
			return Long.parseLong(dbObject.get(fieldName).toString());
		}

		throw new IllegalArgumentException("DBObject does not contain field " + fieldName + ": " + dbObject);
	}
}
