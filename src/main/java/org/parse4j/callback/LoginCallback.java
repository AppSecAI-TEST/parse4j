package org.parse4j.callback;

import org.parse4j.ParseException;
import org.parse4j.ParseUser;

public abstract class LoginCallback extends ParseCallback<ParseUser> {

	abstract void done(ParseUser parseUser, ParseException parseException);
	
	@Override
	void internalDone(ParseUser paramT, ParseException parseException) {
		// TODO Auto-generated method stub
		
	}
	
}