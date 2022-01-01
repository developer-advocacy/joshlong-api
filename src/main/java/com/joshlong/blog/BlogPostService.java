package com.joshlong.blog;

import java.io.File;

public interface BlogPostService {

	BlogPost buildBlogPostFrom(String path, File file);

}
