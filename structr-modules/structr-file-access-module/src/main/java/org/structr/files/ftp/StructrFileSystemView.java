/**
 * Copyright (C) 2010-2017 Structr GmbH
 *
 * This file is part of Structr <http://structr.org>.
 *
 * Structr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Structr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.files.ftp;

import org.apache.commons.lang3.StringUtils;
import org.apache.ftpserver.ftplet.FileSystemView;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpFile;
import org.apache.ftpserver.ftplet.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.common.AccessMode;
import org.structr.common.PathHelper;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.Principal;
import org.structr.core.graph.Tx;
import org.structr.rest.auth.AuthHelper;
import org.structr.web.common.FileHelper;
import org.structr.web.entity.AbstractFile;
import org.structr.web.entity.File;
import org.structr.web.entity.Folder;
import org.structr.web.entity.dom.Page;

/**
 *
 *
 */
public class StructrFileSystemView implements FileSystemView {

	private static final Logger logger = LoggerFactory.getLogger(StructrFileSystemView.class.getName());
	private StructrFtpUser user = null;
	private SecurityContext securityContext = null;

	private String workingDir = "/";

	public StructrFileSystemView(final User user) {

		try (Tx tx = StructrApp.getInstance().tx()) {

			org.structr.web.entity.User structrUser = (org.structr.web.entity.User) AuthHelper.getPrincipalForCredential(Principal.name, user.getName());

			securityContext = SecurityContext.getInstance(structrUser, AccessMode.Backend);

			this.user = new StructrFtpUser(securityContext, structrUser);

			tx.success();

		} catch (FrameworkException fex) {
			logger.error("Error while initializing file system view", fex);
		}
	}

	@Override
	public FtpFile getHomeDirectory() throws FtpException {

		try (Tx tx = StructrApp.getInstance(securityContext).tx()) {

			org.structr.web.entity.User structrUser = (org.structr.web.entity.User) AuthHelper.getPrincipalForCredential(Principal.name, user.getName());

			final Folder homeDir = structrUser.getProperty(org.structr.web.entity.User.homeDirectory);

			tx.success();

			return new StructrFtpFolder(securityContext, homeDir);

		} catch (FrameworkException fex) {
			logger.error("Error while getting home directory", fex);
		}

		return null;
	}

	@Override
	public FtpFile getWorkingDirectory() throws FtpException {

		try (Tx tx = StructrApp.getInstance(securityContext).tx()) {

			AbstractFile structrWorkingDir = FileHelper.getFileByAbsolutePath(securityContext, workingDir);

			tx.success();

			if (structrWorkingDir == null || structrWorkingDir instanceof File) {
				return new StructrFtpFolder(securityContext, null);
			}

			return new StructrFtpFolder(securityContext, (Folder) structrWorkingDir);

		} catch (FrameworkException fex) {
			logger.error("Error in changeWorkingDirectory()", fex);
		}

		return null;
	}

	@Override
	public boolean changeWorkingDirectory(String requestedPath) throws FtpException {

		try (Tx tx = StructrApp.getInstance(securityContext).tx()) {

			final StructrFtpFolder newWorkingDirectory = (StructrFtpFolder) getFile(requestedPath);

			workingDir = newWorkingDirectory.getAbsolutePath();

			tx.success();

			return true;

		} catch (FrameworkException fex) {
			logger.error("Error in changeWorkingDirectory()", fex);
		}

		return false;
	}

	@Override
	public FtpFile getFile(final String rawRequestedPath) throws FtpException {

		String requestedPath = rawRequestedPath;

		// remove trailing slash
		if (requestedPath.endsWith("/")) {
			requestedPath = requestedPath.substring(0, requestedPath.length() - 1);
		}

		logger.info("Requested path: {}, cleaned to {}", rawRequestedPath, requestedPath);

		try (Tx tx = StructrApp.getInstance(securityContext).tx()) {

			if (StringUtils.isBlank(requestedPath) || "/".equals(requestedPath)) {
				return getHomeDirectory();
			}

			StructrFtpFolder cur = (StructrFtpFolder) getWorkingDirectory();

			if (".".equals(requestedPath) || "./".equals(requestedPath)) {
				return cur;
			}

			if ("..".equals(requestedPath) || "../".equals(requestedPath)) {
				return new StructrFtpFolder(securityContext, cur.getStructrFile().getParent());
			}

			// If relative path requested, prepend base path
			if (!requestedPath.startsWith("/")) {

				String basePath = cur.getAbsolutePath();

				logger.info("Base path: {}", basePath);

				while (requestedPath.startsWith("..")) {
					requestedPath = StringUtils.stripStart(StringUtils.stripStart(requestedPath, ".."), "/");
					basePath = StringUtils.substringBeforeLast(basePath, "/");
				}

				requestedPath = StringUtils.stripEnd(basePath.equals("/") ? "/".concat(requestedPath) : basePath.concat("/").concat(requestedPath), "/");

				logger.info("Base path: {}, requestedPath: {}", new Object[]{basePath, requestedPath});

			}

			AbstractFile file = FileHelper.getFileByAbsolutePath(securityContext, requestedPath);


			if (file != null) {

				if (file instanceof Folder) {
					tx.success();
					return new StructrFtpFolder(securityContext, (Folder) file);
				} else {
					tx.success();
					return new StructrFtpFile(securityContext, (File) file);
				}
			}

			// Look up a page by its name
			Page page = StructrApp.getInstance(securityContext).nodeQuery(Page.class).andName(PathHelper.getName(requestedPath)).getFirst();
			if (page != null) {

				tx.success();

				return new FtpFilePageWrapper(page);
			}

			logger.warn("No existing file found: {}", requestedPath);

			tx.success();
			return new FileOrFolder(requestedPath, user);

		} catch (FrameworkException fex) {
			logger.error("Error in getFile()", fex);
		}

		return null;

	}

	@Override
	public boolean isRandomAccessible() throws FtpException {
		logger.info("isRandomAccessible(), returning true");
		return true;
	}

	@Override
	public void dispose() {
		logger.info("dispose() does nothing");
	}

}
