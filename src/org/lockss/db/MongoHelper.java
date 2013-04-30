package org.lockss.db;

import java.nio.ByteBuffer;

import org.bson.types.ObjectId;

import com.mongodb.DBObject;

public class MongoHelper {
	
	private MongoHelper() {}
	
	public static Long objectIdToLongId(ObjectId objectId) {
		byte[] objectIdArray = objectId.toByteArray();
		byte[] byteArray = new byte[8];
		int offset = 5;
		for (int i = offset; i < offset+7; i++) {
			byteArray[i - offset + 1] = objectIdArray[i];
		}
		ByteBuffer bb = ByteBuffer.wrap(byteArray);
		Long result = bb.getLong();
		return Long.valueOf(result);
	}

	public static long readLong(DBObject dbObject, String fieldName) {
		if (dbObject.containsField(fieldName)) {
			return Long.parseLong(dbObject.get(fieldName).toString());
		}

		throw new IllegalArgumentException("DBObject does not contain field " + fieldName + ": " + dbObject);
	}
}
