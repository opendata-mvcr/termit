/**
 * TermIt Copyright (C) 2019 Czech Technical University in Prague
 * <p>
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program.  If not, see
 * <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.EnableMBeanExport;
import cz.cvut.kbss.termit.workspace.WorkspaceStore;
import org.springframework.web.context.annotation.SessionScope;

import javax.servlet.http.HttpSession;

@Configuration
@EnableMBeanExport
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AppConfig {

    @Bean(name = "workspaceStore")
    @SessionScope
    public WorkspaceStore workspaceStore(HttpSession session) {
        return new WorkspaceStore(session);
    }
}
