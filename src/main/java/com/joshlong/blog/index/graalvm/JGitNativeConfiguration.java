package com.joshlong.blog.index.graalvm;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.lib.CoreConfig;
import org.springframework.nativex.hint.NativeHint;
import org.springframework.nativex.hint.ResourceHint;
import org.springframework.nativex.hint.TypeAccess;
import org.springframework.nativex.hint.TypeHint;
import org.springframework.nativex.type.NativeConfiguration;

/**
 * Provides Spring Native hints for the JGit library
 */
@NativeHint(options = "--enable-url-protocols=https")
@ResourceHint(patterns = "org.eclipse.jgit.internal.JGitText", isBundle = true)
@TypeHint(
		types = { CoreConfig.AutoCRLF.class, CoreConfig.CheckStat.class, CoreConfig.EOL.class,
				CoreConfig.HideDotFiles.class, CoreConfig.EolStreamType.class, CoreConfig.LogRefUpdates.class,
				CoreConfig.SymLinks.class, org.eclipse.jgit.internal.JGitText.class, },
		access = { TypeAccess.DECLARED_CLASSES, TypeAccess.DECLARED_CONSTRUCTORS, TypeAccess.DECLARED_FIELDS,
				TypeAccess.DECLARED_METHODS })
@Slf4j
public class JGitNativeConfiguration implements NativeConfiguration {

	JGitNativeConfiguration() {
		log.info("contributing Spring Native hints" + this.getClass().getName());
	}

}
