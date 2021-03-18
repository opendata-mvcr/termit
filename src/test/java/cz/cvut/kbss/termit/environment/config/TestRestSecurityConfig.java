/**
 * TermIt Copyright (C) 2019 Czech Technical University in Prague
 * <p>
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.environment.config;

import cz.cvut.kbss.termit.service.security.SecurityUtils;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

import static org.mockito.Mockito.mock;

/**
 * This configuration class is necessary when testing security of REST controllers (e.g., {@link cz.cvut.kbss.termit.rest.UserController}).
 */
@TestConfiguration
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class TestRestSecurityConfig extends WebSecurityConfigurerAdapter {

    @Bean
    public SecurityUtils securityUtils() {
        return mock(SecurityUtils.class);
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        return mock(AuthenticationProvider.class);
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.authorizeRequests().antMatchers("/rest/query").permitAll().and()
                .authorizeRequests().antMatchers("/**").permitAll()
                .and().exceptionHandling().authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                .and();
    }
}
