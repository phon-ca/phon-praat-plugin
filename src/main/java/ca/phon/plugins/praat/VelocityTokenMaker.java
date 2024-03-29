/*
 * Copyright (C) 2012-2018 Gregory Hedlund
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at

 *    http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ca.phon.plugins.praat;

import org.fife.ui.rsyntaxtextarea.*;

import javax.swing.text.Segment;

public class VelocityTokenMaker extends AbstractTokenMaker {

	private int currentTokenStart;
	
	private int currentTokenType;
	
	@Override
	public void addToken(Segment segment, int start, int end, int tokenType, int startOffset) {
	   // This assumes all keywords, etc. were parsed as "identifiers."
	   if (tokenType==Token.IDENTIFIER) {
	      int value = wordsToHighlight.get(segment, start, end);
	      if (value != -1) {
	         tokenType = value;
	      }
	   }
	   super.addToken(segment, start, end, tokenType, startOffset);
	}
	
	@Override
	public Token getTokenList(Segment text, int initialTokenType,
			int startOffset) {
		resetTokenList();
		
		 char[] array = text.array;
	   int offset = text.offset;
	   int count = text.count;
	   int end = offset + count;

	   // Token starting offsets are always of the form:
	   // 'startOffset + (currentTokenStart-offset)', but since startOffset and
	   // offset are constant, tokens' starting positions become:
	   // 'newStartOffset+currentTokenStart'.
	   int newStartOffset = startOffset - offset;

	   currentTokenStart = offset;
	   currentTokenType  = initialTokenType;
		
		for (int i=offset; i<end; i++) {

		      char c = array[i];

		      switch (currentTokenType) {

		         case Token.NULL:

		            currentTokenStart = i;   // Starting a new token here.

		            switch (c) {

		               case ' ':
		               case '\t':
		                  currentTokenType = Token.WHITESPACE;
		                  break;

		               case '"':
		                  currentTokenType = Token.LITERAL_STRING_DOUBLE_QUOTE;
		                  break;

		               case '#':
		                  currentTokenType = Token.COMMENT_EOL;
		                  break;

		               default:
		                  if (RSyntaxUtilities.isDigit(c)) {
		                     currentTokenType = Token.LITERAL_NUMBER_DECIMAL_INT;
		                     break;
		                  }
		                  else if (RSyntaxUtilities.isLetter(c) || c=='/' || c=='_') {
		                     currentTokenType = Token.IDENTIFIER;
		                     break;
		                  }
		                  
		                  // Anything not currently handled - mark as an identifier
		                  currentTokenType = Token.IDENTIFIER;
		                  break;

		            } // End of switch (c).

		            break;

		         case Token.WHITESPACE:

		            switch (c) {

		               case ' ':
		               case '\t':
		                  break;   // Still whitespace.

		               case '"':
		                  addToken(text, currentTokenStart,i-1, Token.WHITESPACE, newStartOffset+currentTokenStart);
		                  currentTokenStart = i;
		                  currentTokenType = Token.LITERAL_STRING_DOUBLE_QUOTE;
		                  break;

		               case '#':
		                  addToken(text, currentTokenStart,i-1, Token.WHITESPACE, newStartOffset+currentTokenStart);
		                  currentTokenStart = i;
		                  currentTokenType = Token.COMMENT_EOL;
		                  break;

		               default:   // Add the whitespace token and start anew.

		                  addToken(text, currentTokenStart,i-1, Token.WHITESPACE, newStartOffset+currentTokenStart);
		                  currentTokenStart = i;

		                  if (RSyntaxUtilities.isDigit(c)) {
		                     currentTokenType = Token.LITERAL_NUMBER_DECIMAL_INT;
		                     break;
		                  }
		                  else if (RSyntaxUtilities.isLetter(c) || c=='/' || c=='_') {
		                     currentTokenType = Token.IDENTIFIER;
		                     break;
		                  }

		                  // Anything not currently handled - mark as identifier
		                  currentTokenType = Token.IDENTIFIER;

		            } // End of switch (c).

		            break;

		         default: // Should never happen
		         case Token.IDENTIFIER:

		            switch (c) {

		               case ' ':
		               case '\t':
		                  addToken(text, currentTokenStart,i-1, Token.IDENTIFIER, newStartOffset+currentTokenStart);
		                  currentTokenStart = i;
		                  currentTokenType = Token.WHITESPACE;
		                  break;

		               case '"':
		                  addToken(text, currentTokenStart,i-1, Token.IDENTIFIER, newStartOffset+currentTokenStart);
		                  currentTokenStart = i;
		                  currentTokenType = Token.LITERAL_STRING_DOUBLE_QUOTE;
		                  break;

		               default:
		                  if (RSyntaxUtilities.isLetterOrDigit(c) || c=='/' || c=='_') {
		                     break;   // Still an identifier of some type.
		                  }
		                  // Otherwise, we're still an identifier (?).

		            } // End of switch (c).

		            break;

		         case Token.LITERAL_NUMBER_DECIMAL_INT:

		            switch (c) {

		               case ' ':
		               case '\t':
		                  addToken(text, currentTokenStart,i-1, Token.LITERAL_NUMBER_DECIMAL_INT, newStartOffset+currentTokenStart);
		                  currentTokenStart = i;
		                  currentTokenType = Token.WHITESPACE;
		                  break;

		               case '"':
		                  addToken(text, currentTokenStart,i-1, Token.LITERAL_NUMBER_DECIMAL_INT, newStartOffset+currentTokenStart);
		                  currentTokenStart = i;
		                  currentTokenType = Token.LITERAL_STRING_DOUBLE_QUOTE;
		                  break;

		               default:

		                  if (RSyntaxUtilities.isDigit(c)) {
		                     break;   // Still a literal number.
		                  }

		                  // Otherwise, remember this was a number and start over.
		                  addToken(text, currentTokenStart,i-1, Token.LITERAL_NUMBER_DECIMAL_INT, newStartOffset+currentTokenStart);
		                  i--;
		                  currentTokenType = Token.NULL;

		            } // End of switch (c).

		            break;

		         case Token.COMMENT_EOL:
		            i = end - 1;
		            addToken(text, currentTokenStart,i, currentTokenType, newStartOffset+currentTokenStart);
		            // We need to set token type to null so at the bottom we don't add one more token.
		            currentTokenType = Token.NULL;
		            break;

		         case Token.LITERAL_STRING_DOUBLE_QUOTE:
		            if (c=='"') {
		               addToken(text, currentTokenStart,i, Token.LITERAL_STRING_DOUBLE_QUOTE, newStartOffset+currentTokenStart);
		               currentTokenType = Token.NULL;
		            }
		            break;
		            
		      }
		}
		
		switch (currentTokenType) {

	      // Remember what token type to begin the next line with.
	      case Token.LITERAL_STRING_DOUBLE_QUOTE:
	         addToken(text, currentTokenStart,end-1, currentTokenType, newStartOffset+currentTokenStart);
	         break;

	      // Do nothing if everything was okay.
	      case Token.NULL:
	         addNullToken();
	         break;

	      // All other token types don't continue to the next line...
	      default:
	         addToken(text, currentTokenStart,end-1, currentTokenType, newStartOffset+currentTokenStart);
	         addNullToken();

	   }

	   // Return the first token in our linked list.
	   return firstToken;
	}

	@Override
	public TokenMap getWordsToHighlight() {
		final TokenMap tokenMap = new TokenMap();

		tokenMap.put("##", org.fife.ui.rsyntaxtextarea.Token.COMMENT_MARKUP);
		tokenMap.put("#*", Token.COMMENT_MULTILINE);
		tokenMap.put("*#", Token.COMMENT_MULTILINE);

		tokenMap.put("$", Token.VARIABLE);
		tokenMap.put("#if", Token.MARKUP_PROCESSING_INSTRUCTION);
		tokenMap.put("#end", Token.MARKUP_PROCESSING_INSTRUCTION);

		return tokenMap;
	}

}