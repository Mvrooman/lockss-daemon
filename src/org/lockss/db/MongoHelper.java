package org.lockss.db;

import java.nio.ByteBuffer;

import org.bson.types.ObjectId;

import com.mongodb.DBObject;

public class MongoHelper {
	
	private MongoHelper() {}
	
	/**
	 * Create a long-based id out of the given Mongo object ID.
	 * @param objectId
	 * @return Long The object
	 */
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

	/**
	 * Read a long from the given DBObject.
	 * @param dbObject The DBObject containing the Long.
	 * @param fieldName The name of the Long field.
	 * @return long The named long.
	 */
	public static long readLong(DBObject dbObject, String fieldName) {
		if (dbObject.containsField(fieldName)) {
			return Long.parseLong(dbObject.get(fieldName).toString());
		}

		throw new IllegalArgumentException("DBObject does not contain field " + fieldName + ": " + dbObject);
	}
}
