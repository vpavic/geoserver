/* Copyright (c) 2012 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.security.validation;

import static org.easymock.EasyMock.expect;
import static org.easymock.classextension.EasyMock.createNiceMock;
import static org.easymock.classextension.EasyMock.replay;
import static org.geoserver.security.validation.SecurityConfigException.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.IOException;

import org.geoserver.security.GeoServerAuthenticationProvider;
import org.geoserver.security.GeoServerRoleService;
import org.geoserver.security.GeoServerUserGroupService;
import org.geoserver.security.auth.UsernamePasswordAuthenticationProvider;
import org.geoserver.security.config.BaseSecurityNamedServiceConfig;
import org.geoserver.security.config.PasswordPolicyConfig;
import org.geoserver.security.config.SecurityAuthProviderConfig;
import org.geoserver.security.config.SecurityManagerConfig;
import org.geoserver.security.config.SecurityNamedServiceConfig;
import org.geoserver.security.config.SecurityRoleServiceConfig;
import org.geoserver.security.config.SecurityUserGroupServiceConfig;
import org.geoserver.security.config.UsernamePasswordAuthenticationProviderConfig;
import org.geoserver.security.config.impl.MemoryRoleServiceConfigImpl;
import org.geoserver.security.config.impl.MemoryUserGroupServiceConfigImpl;
import org.geoserver.security.filter.GeoServerSecurityFilter;
import org.geoserver.security.impl.GeoServerRole;
import org.geoserver.security.impl.MemoryRoleService;
import org.geoserver.security.impl.MemoryUserGroupService;
import org.geoserver.security.password.PasswordValidator;
import org.geoserver.security.xml.XMLRoleService;
import org.geoserver.security.xml.XMLSecurityConfigValidator;
import org.geoserver.security.xml.XMLUserGroupService;
import org.geoserver.test.GeoServerMockTestSupport;
import org.junit.Test;

public class SecurityConfigValidatorTest extends GeoServerMockTestSupport {

    @Test
    public void testMasterConfigValidation() throws Exception{
        SecurityManagerConfig config = new SecurityManagerConfig();
        config.setRoleServiceName(XMLRoleService.DEFAULT_NAME);
        config.setConfigPasswordEncrypterName(getPBEPasswordEncoder().getName());
        config.getAuthProviderNames().add(GeoServerAuthenticationProvider.DEFAULT_NAME);

        SecurityConfigValidator validator = new SecurityConfigValidator(getSecurityManager());
        validator.validateManagerConfig(config);

        try {
            config.setConfigPasswordEncrypterName("abc");
            validator.validateManagerConfig(config);
            fail("invalid password encoder should fail");
        } catch (SecurityConfigException ex){
            assertEquals(INVALID_PASSWORD_ENCODER_$1,ex.getId());
        }

        try {
            config.setConfigPasswordEncrypterName(null);
            validator.validateManagerConfig(config);
            fail("no password encoder should fail");
        } catch (SecurityConfigException ex){
            assertEquals(PASSWORD_ENCODER_REQUIRED,ex.getId());
        }
        
        if (getSecurityManager().isStrongEncryptionAvailable()==false) {
            config.setConfigPasswordEncrypterName(getStrongPBEPasswordEncoder().getName());
            try {
                validator.validateManagerConfig(config);
                fail("invalid strong password encoder should fail");
            } catch (SecurityConfigException ex){
                assertEquals(INVALID_STRONG_CONFIG_PASSWORD_ENCODER,ex.getId());
            }
        }

        config.setConfigPasswordEncrypterName(getPBEPasswordEncoder().getName());
        config.setRoleServiceName("XX");

        try {
            validator.validateManagerConfig(config);
            fail("unknown role service should fail");
        } catch (SecurityConfigException ex){
            assertEquals(ROLE_SERVICE_NOT_FOUND_$1,ex.getId());
        }
        
        config.setRoleServiceName(null);
        try {
            validator.validateManagerConfig(config);
            fail("null role service should fail");
        } catch (SecurityConfigException ex){
            assertEquals(ROLE_SERVICE_NOT_FOUND_$1,ex.getId());
        }

        config.setRoleServiceName(XMLRoleService.DEFAULT_NAME);
        config.getAuthProviderNames().add("XX");

        try {
            validator.validateManagerConfig(config);
            fail("unknown auth provider should fail");
        } catch (SecurityConfigException ex){
            assertEquals(AUTH_PROVIDER_NOT_FOUND_$1,ex.getId());
        }
    }

    @Test
    public void testNamedServices() {
        SecurityConfigValidator validator = new SecurityConfigValidator(getSecurityManager()); 
        Class<?>[] extensionPoints = new Class<?>[] {
                GeoServerUserGroupService.class,
                GeoServerRoleService.class,
                PasswordValidator.class,
                GeoServerAuthenticationProvider.class,
                GeoServerSecurityFilter.class
        };
        
        for (Class<?> ep : extensionPoints) {
           try {
               validator.checkExtensionPont(ep, "a.b.c");
               fail("unknown class should fail");
           } catch (SecurityConfigException ex) {
               assertEquals(ex.getId(), CLASS_NOT_FOUND_$1);
               assertEquals(ex.getArgs()[0],"a.b.c");
           }
           
           try {
               validator.checkExtensionPont(ep, "java.lang.String");
               fail("wrong class should fail");
           } catch (SecurityConfigException ex) {
               assertEquals(ex.getId(), CLASS_WRONG_TYPE_$2);
               assertEquals(ex.getArgs()[0],ep);
               assertEquals(ex.getArgs()[1],"java.lang.String");
           }

           String className = ep == GeoServerUserGroupService.class ? null : "";
           try {               
               validator.checkExtensionPont(ep, className);
               fail("no class should fail");
           } catch (SecurityConfigException ex) {
               assertEquals(ex.getId(), CLASSNAME_REQUIRED);
               assertEquals(0,ex.getArgs().length);
           }

           String name = ep == GeoServerUserGroupService.class ? null : "";
           try {               
               validator.checkServiceName(ep, name);
               fail("no name should fail");
           } catch (SecurityConfigException ex) {
               assertEquals(ex.getId(), NAME_REQUIRED);
               assertEquals(0,ex.getArgs().length);
               
           }
        }

        // test names
        try {
            validator.validateAddPasswordPolicy(
                createPolicyConfig(PasswordValidator.DEFAULT_NAME, PasswordValidatorImpl.class, 1,10));
            fail("passwd policy already exists should fail");
        } catch (SecurityConfigException ex) {
            assertEquals(PASSWD_POLICY_ALREADY_EXISTS_$1, ex.getId());
            assertEquals(ex.getArgs()[0],PasswordValidator.DEFAULT_NAME);
        }

        PasswordPolicyConfig pwConfig = createPolicyConfig("default2", PasswordValidatorImpl.class, 1,10);
        
        try {
            validator.validateModifiedPasswordPolicy(pwConfig,pwConfig);
            fail("unknown passwd policy should fail");
        } catch (SecurityConfigException ex) {
            assertEquals(PASSWD_POLICY_NOT_FOUND_$1, ex.getId());
            assertEquals(ex.getArgs()[0],"default2");
        }

        try {
            validator.validateAddUserGroupService(
                createUGConfig(XMLUserGroupService.DEFAULT_NAME, GeoServerUserGroupService.class, 
                    getPlainTextPasswordEncoder().getName(),PasswordValidator.DEFAULT_NAME));
            fail("user group service already exists should fail");
        } catch (SecurityConfigException ex) {
            assertEquals(ex.getId(), USERGROUP_SERVICE_ALREADY_EXISTS_$1);
            assertEquals(ex.getArgs()[0],XMLUserGroupService.DEFAULT_NAME);
        }

        SecurityUserGroupServiceConfig ugConfig =  
                createUGConfig("default2", GeoServerUserGroupService.class, 
                getPlainTextPasswordEncoder().getName(), PasswordValidator.DEFAULT_NAME); 
        try {
            validator.validateModifiedUserGroupService(ugConfig,ugConfig);
            fail("unknown user group service should fail");
        } catch (SecurityConfigException ex) {
            assertEquals(ex.getId(), USERGROUP_SERVICE_NOT_FOUND_$1);
            assertEquals(ex.getArgs()[0],"default2");
        }

        try {
            validator.validateAddRoleService(
                createRoleConfig(XMLRoleService.DEFAULT_NAME, GeoServerRoleService.class, 
                        GeoServerRole.ADMIN_ROLE.getAuthority()));
            fail("role service already exists should fail");
        } catch (SecurityConfigException ex) {
            assertEquals(ex.getId(), ROLE_SERVICE_ALREADY_EXISTS_$1);
            assertEquals(ex.getArgs()[0],XMLRoleService.DEFAULT_NAME);
        }

        SecurityRoleServiceConfig config = createRoleConfig("default2", GeoServerRoleService.class, 
                GeoServerRole.ADMIN_ROLE.getAuthority());
        try {
            validator.validateModifiedRoleService(config,config);
            fail("unknown role service should fail");
        } catch (SecurityConfigException ex) {
            assertEquals(ex.getId(), ROLE_SERVICE_NOT_FOUND_$1);
            assertEquals(ex.getArgs()[0],"default2");
        }

        try {
            validator.validateAddAuthProvider(
                createAuthConfig(GeoServerAuthenticationProvider.DEFAULT_NAME, UsernamePasswordAuthenticationProvider.class,
                        XMLUserGroupService.DEFAULT_NAME));
            fail("auth provider already exists should fail");
        } catch (SecurityConfigException ex) {
            assertEquals(ex.getId(), AUTH_PROVIDER_ALREADY_EXISTS_$1);
            assertEquals(ex.getArgs()[0],GeoServerAuthenticationProvider.DEFAULT_NAME);
        }

        SecurityAuthProviderConfig aConfig = createAuthConfig("default2", 
                UsernamePasswordAuthenticationProvider.class, XMLUserGroupService.DEFAULT_NAME);  
        try {
            validator.validateModifiedAuthProvider(aConfig,aConfig);
            fail("unknown auth provider should fail");
        } catch (SecurityConfigException ex) {
            assertEquals(ex.getId(), AUTH_PROVIDER_NOT_FOUND_$1);
            assertEquals(ex.getArgs()[0],"default2");
        }

    }

    protected SecurityAuthProviderConfig createAuthConfig(String name, Class<?> aClass,String userGroupServiceName) {
        SecurityAuthProviderConfig config = new UsernamePasswordAuthenticationProviderConfig();
        config.setName(name);
        config.setClassName(aClass.getName());
        config.setUserGroupServiceName(userGroupServiceName);
        /*SecurityAuthProviderConfig config = createNiceMock(SecurityAuthProviderConfig.class);
        expect(config.getName()).andReturn(name).anyTimes();
        expect(config.getClassName()).andReturn(aClass.getName()).anyTimes();
        expect(config.getUserGroupServiceName()).andReturn(userGroupServiceName).anyTimes();
        replay(config);*/
        return config;
    }
    
    protected SecurityUserGroupServiceConfig createUGConfig(String name, Class<?> aClass,
            String encoder, String policyName) {

        MemoryUserGroupServiceConfigImpl config = new MemoryUserGroupServiceConfigImpl();
        config.setName(name);
        config.setClassName(aClass.getName());
        config.setPasswordEncoderName(encoder);
        config.setPasswordPolicyName(policyName);
        /*SecurityUserGroupServiceConfig ugConfig = createNiceMock(SecurityUserGroupServiceConfig.class);
        expect(ugConfig.getName()).andReturn(name).anyTimes();
        expect(ugConfig.getClassName()).andReturn(aClass.getName()).anyTimes();
        expect(ugConfig.getPasswordEncoderName()).andReturn(encoder).anyTimes();
        expect(ugConfig.getPasswordPolicyName()).andReturn(policyName).anyTimes();
        replay(ugConfig);*/
        return config;
    }

    protected SecurityRoleServiceConfig createRoleConfig(String name, Class<?> aClass,String adminRole) {
        SecurityRoleServiceConfig config = new MemoryRoleServiceConfigImpl();
        config.setName(name);
        config.setClassName(aClass.getName());
        config.setAdminRoleName(adminRole);
        /*SecurityRoleServiceConfig config = createNiceMock(SecurityRoleServiceConfig.class);
        expect(config.getName()).andReturn(name).anyTimes();
        expect(config.getClassName()).andReturn(aClass.getName()).anyTimes();
        expect(config.getAdminRoleName()).andReturn(adminRole).anyTimes();
        replay(config);*/
        return config;
    }

    protected PasswordPolicyConfig createPolicyConfig(String name, Class<?> aClass,int min, int max) {
        PasswordPolicyConfig config = new PasswordPolicyConfig();
        config.setName(name);
        config.setClassName(aClass.getName());
        config.setMinLength(min);
        config.setMaxLength(max);
        return config;
    }

    protected SecurityNamedServiceConfig createFilterConfig(String name, Class<?> aClass) {
        SecurityNamedServiceConfig config = new BaseSecurityNamedServiceConfig();
        config.setName(name);
        config.setClassName(aClass.getName());
        return config;
    }

    @Test
    public void testPasswordPolicy() throws IOException {

        SecurityConfigValidator validator = new SecurityConfigValidator(getSecurityManager());
        PasswordPolicyConfig config = 
            createPolicyConfig(PasswordValidator.DEFAULT_NAME, PasswordValidatorImpl.class, -1,10);

        try {
            config.setName("default2");
            validator.validateAddPasswordPolicy(config);
            fail("invalid min length should fail");
            //getSecurityManager().savePasswordPolicy(config);
        } catch (SecurityConfigException ex) {
            assertEquals( INVALID_MIN_LENGTH,ex.getId());
            assertEquals(0,ex.getArgs().length);
        }

        try {
            validator.validateAddPasswordPolicy(config);
            fail("invalid min length should fail");
            //getSecurityManager().savePasswordPolicy(config);
        } catch (SecurityConfigException ex) {
            assertEquals( INVALID_MIN_LENGTH,ex.getId());
            assertEquals(0,ex.getArgs().length);
        }

        config.setMinLength(1);
        config.setMaxLength(0);

        try {
            validator.validateAddPasswordPolicy(config);
            fail("invalid max length should fail");
            getSecurityManager().savePasswordPolicy(config);
        } catch (SecurityConfigException ex) {
            assertEquals( INVALID_MAX_LENGTH,ex.getId());
            assertEquals(0,ex.getArgs().length);
        }

        try {
            validator.validateAddPasswordPolicy(config);
            fail("invalid max length should fail");
        } catch (SecurityConfigException ex) {
            assertEquals( INVALID_MAX_LENGTH,ex.getId());
            assertEquals(0,ex.getArgs().length);
        }

        config.setMaxLength(-1);

        try {
            config.setName("");
            validator.validateRemovePasswordPolicy(config);
            fail("no name should fail");
            //getSecurityManager().removePasswordValidator(config);                         
        } catch (SecurityConfigException ex) {
            assertEquals( NAME_REQUIRED,ex.getId());
            assertEquals(0,ex.getArgs().length);
        }

        try {
            config.setName(PasswordValidator.DEFAULT_NAME);
            validator.validateRemovePasswordPolicy(config);
            fail("remove active should fail");
            //getSecurityManager().removePasswordValidator(config);
        } catch (SecurityConfigException ex) {
            assertEquals( PASSWD_POLICY_ACTIVE_$2,ex.getId());
            assertEquals(PasswordValidator.DEFAULT_NAME,ex.getArgs()[0]);
            assertEquals(XMLUserGroupService.DEFAULT_NAME,ex.getArgs()[1]);
        }

        try {
            config.setName(PasswordValidator.MASTERPASSWORD_NAME);
            validator.validateRemovePasswordPolicy(config);
            fail("remove master should fail");
            //getSecurityManager().removePasswordValidator(config);
        } catch (SecurityConfigException ex) {
            assertEquals( PASSWD_POLICY_MASTER_DELETE,ex.getId());
            assertEquals(0,ex.getArgs().length);
        }
    }

    @Test
    public void testRoleConfig() throws IOException {

        SecurityRoleServiceConfig config = createRoleConfig(XMLRoleService.DEFAULT_NAME, 
            MemoryRoleService.class, GeoServerRole.ADMIN_ROLE.getAuthority());

        SecurityConfigValidator validator = new SecurityConfigValidator(getSecurityManager());
        try {
            config.setName(null);
            validator.validateRemoveRoleService(config);
            fail("no name should fail");
            //getSecurityManager().removeRoleService(config) ;
        } catch (SecurityConfigException ex) {
            assertEquals( NAME_REQUIRED,ex.getId());
            assertEquals(0,ex.getArgs().length);            
        }
        
        config.setName("abcd");
        for (GeoServerRole role : GeoServerRole.SystemRoles) {
            config.setAdminRoleName(role.getAuthority());
            try {
                validator.validateAddRoleService(config);
                fail("reserved role name should fail");
                //getSecurityManager().saveRoleService(config);
            } catch (SecurityConfigException ex) {
                assertEquals( RESERVED_ROLE_NAME,ex.getId());
                assertEquals(role.getAuthority(),ex.getArgs()[0]);
            }
        }
        
        for (GeoServerRole role : GeoServerRole.SystemRoles) {
            config.setGroupAdminRoleName(role.getAuthority());
            try { 
                validator.validateAddRoleService(config);
                fail("resoerved role name should fail");
                //getSecurityManager().saveRoleService(config);                        
            } catch (SecurityConfigException ex) {
                assertEquals( RESERVED_ROLE_NAME,ex.getId());
                assertEquals(role.getAuthority(),ex.getArgs()[0]);            
            }
        }

        try {
            config.setName(XMLRoleService.DEFAULT_NAME);
            validator.validateRemoveRoleService(config);
            fail("role service active should fail");
            //getSecurityManager().removeRoleService(config) ;                         
        } catch (SecurityConfigException ex) {
            assertEquals( ROLE_SERVICE_ACTIVE_$1,ex.getId());
            assertEquals(XMLRoleService.DEFAULT_NAME,ex.getArgs()[0]);
        }
    }

    @Test
    public void testAuthenticationProvider() throws IOException {
        
        SecurityAuthProviderConfig config = createAuthConfig(GeoServerAuthenticationProvider.DEFAULT_NAME, 
                UsernamePasswordAuthenticationProvider.class, "default2");

        SecurityConfigValidator validator = new SecurityConfigValidator(getSecurityManager());
        try {
            config.setName("default2");
            validator.validateAddAuthProvider(config);
            fail("user group service not found should fail");
            //getSecurityManager().saveAuthenticationProvider(config);
        } catch (SecurityConfigException ex) {
            assertEquals( USERGROUP_SERVICE_NOT_FOUND_$1,ex.getId());
            assertEquals("default2",ex.getArgs()[0]);
        }

        try {
            config.setName("other");
            validator.validateAddAuthProvider(config);
            fail("user group service not found should fail");
            //getSecurityManager().saveAuthenticationProvider(config);
        } catch (SecurityConfigException ex) {
            assertEquals( USERGROUP_SERVICE_NOT_FOUND_$1,ex.getId());
            assertEquals("default2",ex.getArgs()[0]);
        }

        try {
            config.setName("");
            validator.validateRemoveAuthProvider(config);
            fail("no name should fail");
            //getSecurityManager().removeAuthenticationProvider(config);
        } catch (SecurityConfigException ex) {
            assertEquals( NAME_REQUIRED,ex.getId());
            assertEquals(0,ex.getArgs().length);
        }

        try {
            config.setName(GeoServerAuthenticationProvider.DEFAULT_NAME);
            validator.validateRemoveAuthProvider(config);
            fail("active auth provieder should fail");
            //getSecurityManager().removeAuthenticationProvider(config);
        } catch (SecurityConfigException ex) {
            assertEquals( AUTH_PROVIDER_ACTIVE_$1,ex.getId());
            assertEquals(GeoServerAuthenticationProvider.DEFAULT_NAME,ex.getArgs()[0]);
        }
    }

    @Test
    public void testUserGroupConfig() throws IOException {
        
        SecurityUserGroupServiceConfig config = createUGConfig(XMLUserGroupService.DEFAULT_NAME, 
            MemoryUserGroupService.class, getPlainTextPasswordEncoder().getName(),PasswordValidator.DEFAULT_NAME);

        SecurityConfigValidator validator = new SecurityConfigValidator(getSecurityManager());

        try {
            config.setName("default2");
            config.setPasswordEncoderName("xxx");
            validator.validateAddUserGroupService(config);
            fail("invalid config password encoder should fail");
            //getSecurityManager().saveUserGroupService(config);                         
        } catch (SecurityConfigException ex) {
            assertEquals( INVALID_CONFIG_PASSWORD_ENCODER_$1,ex.getId());
            assertEquals("xxx",ex.getArgs()[0]);
        }

        if (!getSecurityManager().isStrongEncryptionAvailable()) {
            config.setPasswordEncoderName(getStrongPBEPasswordEncoder().getName());
            
            try {
                validator.validateAddUserGroupService(config);
                fail("invalid strong password encoder should fail");
                //getSecurityManager().saveUserGroupService(config);
            } catch (SecurityConfigException ex){
                assertEquals(INVALID_STRONG_PASSWORD_ENCODER,ex.getId());
            }
        }

        try {
            config.setName("other");
            config.setPasswordEncoderName("xxx");
            validator.validateAddUserGroupService(config);
            fail("invalid config password encoder should fail");
            //getSecurityManager().saveUserGroupService(config);                         
        } catch (SecurityConfigException ex) {
            assertEquals( INVALID_CONFIG_PASSWORD_ENCODER_$1,ex.getId());
            assertEquals("xxx",ex.getArgs()[0]);
        }

        try {
            config.setName("default2");
            config.setPasswordEncoderName("");
            validator.validateAddUserGroupService(config);
            fail("no password encoder should fail");
            //getSecurityManager().saveUserGroupService(config);                         
        } catch (SecurityConfigException ex) {
            assertEquals( PASSWD_ENCODER_REQUIRED_$1,ex.getId());
            assertEquals("default2",ex.getArgs()[0]);
        }

        try {
            config.setName("default3");
            config.setPasswordEncoderName(null);
            //getSecurityManager().saveUserGroupService(config);
            validator.validateAddUserGroupService(config);
            fail("no password encoder should fail");
        } catch (SecurityConfigException ex) {
            assertEquals( PASSWD_ENCODER_REQUIRED_$1,ex.getId());
            assertEquals("default3", ex.getArgs()[0]);
        }

        config.setPasswordEncoderName(getPlainTextPasswordEncoder().getName());

        try {
            config.setName("default2");
            config.setPasswordPolicyName("default2");
            validator.validateAddUserGroupService(config);
            fail("unknown password policy should fail");
            //getSecurityManager().saveUserGroupService(config);                         
        } catch (SecurityConfigException ex) {
            assertEquals( PASSWD_POLICY_NOT_FOUND_$1,ex.getId());
            assertEquals("default2",ex.getArgs()[0]);
        }

        try {
            config.setName("default3");
            config.setPasswordPolicyName("default2");
            validator.validateAddUserGroupService(config);
            fail("unkonwn password policy encoder should fail");
            //getSecurityManager().saveUserGroupService(config);                         
        } catch (SecurityConfigException ex) {
            assertEquals( PASSWD_POLICY_NOT_FOUND_$1,ex.getId());
            assertEquals("default2",ex.getArgs()[0]);
        }
        
        try {
            config.setName("default2");
            config.setPasswordPolicyName("");
            //getSecurityManager().saveUserGroupService(config);
            validator.validateAddUserGroupService(config);
            fail("no password policy should fail");
        } catch (SecurityConfigException ex) {
            assertEquals( PASSWD_POLICY_REQUIRED_$1,ex.getId());
            assertEquals("default2",ex.getArgs()[0]);
        }

        try {
            config.setName("default3");
            config.setPasswordPolicyName(null);
            validator.validateAddUserGroupService(config);
            fail("invalidate password policy should fail");
            //getSecurityManager().saveUserGroupService(config);                         
        } catch (SecurityConfigException ex) {
            assertEquals( PASSWD_POLICY_REQUIRED_$1,ex.getId());
            assertEquals("default3",ex.getArgs()[0]);
        }

        try {
            config.setName(null);
            validator.validateRemoveUserGroupService(config);
            fail("no name should fail");
            getSecurityManager().removeUserGroupService(config);                         
        } catch (SecurityConfigException ex) {
            assertEquals( NAME_REQUIRED,ex.getId());
            assertEquals(0,ex.getArgs().length);
        }

        try {
            config.setName(XMLUserGroupService.DEFAULT_NAME);
            validator.validateRemoveUserGroupService(config);
            fail("active user group service should fail");
            //getSecurityManager().removeUserGroupService(config);                         
        } catch (SecurityConfigException ex) {
            assertEquals( USERGROUP_SERVICE_ACTIVE_$2,ex.getId());
            assertEquals(XMLUserGroupService.DEFAULT_NAME,ex.getArgs()[0]);
            assertEquals(GeoServerAuthenticationProvider.DEFAULT_NAME,ex.getArgs()[1]);
        }

    }

}
