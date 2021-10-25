package com.joshlong.blog;

import java.util.Collection;
import java.util.Map;

public interface IndexService {

	IndexRebuildStatus rebuildIndex();

	Collection<BlogPost> search(String query);

	Map<String, BlogPost> getIndex();

}
