package com.joshlong.blog;

import java.util.Map;

public interface IndexService {

	/**
	 * Optionally clones a source git repository and then adds the content of that git
	 * repository to a Lucene index
	 */
	IndexRebuildStatus rebuildIndex();

	/**
	 * Searches the Lucene index. Supports Lucene queries like
	 * <CODE> title:2019 and content:"india china"</CODE>
	 */
	BlogPostSearchResults search(String query, int offset, int size, boolean includeListed);

	Map<String, BlogPost> getIndex();

}
