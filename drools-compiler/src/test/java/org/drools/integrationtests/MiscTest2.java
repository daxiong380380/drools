/*
 * Copyright 2005 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.drools.integrationtests;

import org.drools.Address;
import org.drools.CommonTestMethodBase;
import org.drools.KnowledgeBase;
import org.drools.KnowledgeBaseFactory;
import org.drools.Person;
import org.drools.WorkingMemory;
import org.drools.builder.KnowledgeBuilder;
import org.drools.builder.KnowledgeBuilderFactory;
import org.drools.builder.ResourceType;
import org.drools.core.util.FileManager;
import org.drools.definition.KnowledgePackage;
import org.drools.event.ActivationCancelledEvent;
import org.drools.event.ActivationCreatedEvent;
import org.drools.event.AfterActivationFiredEvent;
import org.drools.event.rule.AgendaEventListener;
import org.drools.event.AgendaGroupPoppedEvent;
import org.drools.event.AgendaGroupPushedEvent;
import org.drools.event.BeforeActivationFiredEvent;
import org.drools.event.RuleFlowGroupActivatedEvent;
import org.drools.event.RuleFlowGroupDeactivatedEvent;
import org.drools.event.knowledgebase.DefaultKnowledgeBaseEventListener;
import org.drools.event.knowledgebase.KnowledgeBaseEventListener;
import org.drools.impl.KnowledgeBaseImpl;
import org.drools.io.ResourceFactory;
import org.drools.marshalling.impl.ProtobufMessages;
import org.drools.runtime.StatefulKnowledgeSession;
import org.drools.runtime.rule.FactHandle;
import org.drools.runtime.rule.impl.AgendaImpl;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Run all the tests with the ReteOO engine implementation
 */
public class MiscTest2 extends CommonTestMethodBase {

    private static final Logger logger = LoggerFactory.getLogger(MiscTest2.class);

    @Test
    public void testUpdateWithNonEffectiveActivations() throws Exception {
        // JBRULES-3604
        String str = "package inheritance\n" +
                "\n" +
                "import org.drools.Address\n" +
                "\n" +
                "rule \"Parent\"\n" +
                "    enabled false\n" +
                "    when \n" +
                "        $a : Address(suburb == \"xyz\")\n" +
                "    then \n" +
                "        System.out.println( $a ); \n" +
                "end \n" +
                "rule \"Child\" extends \"Parent\" \n" +
                "    when \n" +
                "        $b : Address( this == $a, street == \"123\")\n" +
                "    then \n" +
                "        System.out.println( $b ); \n" +
                "end";

        KnowledgeBuilder builder = KnowledgeBuilderFactory.newKnowledgeBuilder();

        builder.add( ResourceFactory.newByteArrayResource( str.getBytes() ), ResourceType.DRL);

        if ( builder.hasErrors() ) {
            throw new RuntimeException(builder.getErrors().toString());
        }
        KnowledgeBase knowledgeBase = KnowledgeBaseFactory.newKnowledgeBase();
        knowledgeBase.addKnowledgePackages(builder.getKnowledgePackages());

        StatefulKnowledgeSession ksession = knowledgeBase.newStatefulKnowledgeSession();

        Address address = new Address();

        address.setSuburb("xyz");
        org.drools.runtime.rule.FactHandle addressHandle = ksession.insert(address);

        int rulesFired = ksession.fireAllRules();

        assertEquals( 0, rulesFired );

        address.setStreet("123");


        ksession.update(addressHandle, address);

        rulesFired = ksession.fireAllRules();

        System.out.println( rulesFired );
        assertEquals( 1, rulesFired );

        ksession.dispose();
    }

    @Test
    public void testClassNotFoundAfterDeserialization() throws Exception {
        // JBRULES-3670
        String drl =
                "package completely.other.deal;\n" +
                "\n" +
                "declare Person\n" +
                "   firstName : String\n" +
                "   lastName : String\n" +
                "end\n" +
                "\n" +
                "rule \"now use it B\"\n" +
                "   when\n" +
                "       Person( $christianName, $surname; )\n" +
                "   then\n" +
                "       insert( new Person( $christianName, null ) );\n" +
                "end";

        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilder.add( ResourceFactory.newByteArrayResource(drl.getBytes()), ResourceType.DRL );

        if ( kbuilder.hasErrors() ) {
            throw new RuntimeException("" + kbuilder.getErrors());
        }

        FileManager fileManager = new FileManager();
        fileManager.setUp();

        try {
            File root = fileManager.getRootDirectory();

            ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(new File(root, "test.drl.compiled")));
            out.writeObject( kbuilder.getKnowledgePackages());
            out.close();

            KnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase();

            ObjectInputStream in = new ObjectInputStream(new FileInputStream(new File(root, "test.drl.compiled")));
            kbase.addKnowledgePackages((Collection<KnowledgePackage>) in.readObject());
            in.close();
        } finally {
            fileManager.tearDown();
        }
    }

    @Test
    public void testAnalyzeConditionWithVariableRegExp() throws Exception {
        // JBRULES-3659
        String str =
                "dialect \"mvel\"\n" +
                "\n" +
                "declare Person\n" +
                "   name : String\n" +
                "end\n" +
                "declare Stuff\n" +
                "   regexp : String\n" +
                "end\n" +
                "\n" +
                "rule \"Test Regex\"\n" +
                "   salience 100\n" +
                "    when\n" +
                "    then\n" +
                "       insert (new Stuff(\"Test\"));\n" +
                "       insert (new Person(\"Test\"));\n" +
                "end\n" +
                "\n" +
                "rule \"Test Equality\"\n" +
                "   salience 10\n" +
                "    when\n" +
                "       Stuff( $regexp : regexp )\n" +
                "        Person( name matches $regexp )\n" +
                "        //Person( name matches \"Test\" )\n" +
                "    then\n" +
                "end\n";

        KnowledgeBase kbase = loadKnowledgeBaseFromString(str);
        StatefulKnowledgeSession ksession = kbase.newStatefulKnowledgeSession();

        assertEquals(2, ksession.fireAllRules());
    }

    @Test
    public void testShareAlphaNodesRegardlessDoubleOrSingleQuotes() {
        // JBRULES-3640
        String str =
                "declare RecordA\n" +
                "   id : long\n" +
                "end\n" +
                "\n" +
                "declare RecordB\n" +
                "   id : long\n" +
                "role : String\n" +
                "end\n" +
                "\n" +
                "rule \"insert data 1\"\n" +
                "   salience 10\n" +
                "   when\n" +
                "   then\n" +
                "       insert (new RecordA(100));\n" +
                "       insert (new RecordB(100, \"1\"));\n" +
                "       insert (new RecordB(100, \"2\"));\n" +
                "end\n" +
                "\n" +
                "rule \"test 1\"\n" +
                "   when\n" +
                "       a : RecordA( )\n" +
                "       b : RecordB( id == b.id, role == '1' )\n" +
                "   then\n" +
                "end\n" +
                "\n" +
                "rule \"test 2\"\n" +
                "   when\n" +
                "       a : RecordA( )\n" +
                "       b : RecordB( id == b.id, role == \"1\" )\n" +
                "   then\n" +
                "end\n" +
                "\n" +
                "rule \"test 3\"\n" +
                "   when\n" +
                "       a : RecordA( )\n" +
                "       b : RecordB( id == b.id, role == \"2\" )\n" +
                "   then\n" +
                "end\n";

        KnowledgeBase kbase = loadKnowledgeBaseFromString(str);
        StatefulKnowledgeSession ksession = kbase.newStatefulKnowledgeSession();

        assertEquals(4, ksession.fireAllRules());
    }

    @Test
    public void testKnowledgeBaseEventSupportLeak() throws Exception {
        // JBRULES-3666
        KnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase();
        KnowledgeBaseEventListener listener = new DefaultKnowledgeBaseEventListener();
        kbase.addEventListener(listener);
        kbase.addEventListener(listener);
        assertEquals(1, ((KnowledgeBaseImpl) kbase).getRuleBase().getRuleBaseEventListeners().size());
        kbase.removeEventListener(listener);
        assertEquals(0, ((KnowledgeBaseImpl) kbase).getRuleBase().getRuleBaseEventListeners().size());
    }

    @Test
    public void testReuseAgendaAfterException() throws Exception {
        // JBRULES-3677

        String str = "import org.drools.Person;\n" +
                "global java.util.List results;" +
                "rule R1\n" +
                "ruleflow-group \"test\"\n" +
                "when\n" +
                "   Person( $age : age ) \n" +
                "then\n" +
                "   if ($age > 40) throw new RuntimeException(\"Too old\");\n" +
                "   results.add(\"OK\");" +
                "end";

        KnowledgeBase kbase = loadKnowledgeBaseFromString(str);
        final StatefulKnowledgeSession ksession = kbase.newStatefulKnowledgeSession();

        List<String> res = new ArrayList<String>();
        ksession.setGlobal( "results", res );

        AgendaEventListener agendaEventListener = new AgendaEventListener() {
            public void activationCreated(org.drools.event.rule.ActivationCreatedEvent event) {
            }

            public void activationCancelled(org.drools.event.rule.ActivationCancelledEvent event) {
            }

            public void beforeActivationFired(org.drools.event.rule.BeforeActivationFiredEvent event) {
            }

            public void afterActivationFired(org.drools.event.rule.AfterActivationFiredEvent event) {
            }

            public void agendaGroupPopped(org.drools.event.rule.AgendaGroupPoppedEvent event) {
            }

            public void agendaGroupPushed(org.drools.event.rule.AgendaGroupPushedEvent event) {
            }

            public void beforeRuleFlowGroupActivated(org.drools.event.rule.RuleFlowGroupActivatedEvent event) {
            }

            public void afterRuleFlowGroupActivated(org.drools.event.rule.RuleFlowGroupActivatedEvent event) {
                ksession.fireAllRules();
            }

            public void beforeRuleFlowGroupDeactivated(org.drools.event.rule.RuleFlowGroupDeactivatedEvent event) {
            }

            public void afterRuleFlowGroupDeactivated(org.drools.event.rule.RuleFlowGroupDeactivatedEvent event) {
            }
        };

        ksession.addEventListener(agendaEventListener);

        FactHandle fact1 = ksession.insert(new Person("Mario", 38));
        ((AgendaImpl)ksession.getAgenda()).activateRuleFlowGroup("test");

        assertEquals(1, res.size());
        res.clear();

        ksession.retract(fact1);

        FactHandle fact2 = ksession.insert(new Person("Mario", 48));
        try {
            ((AgendaImpl)ksession.getAgenda()).activateRuleFlowGroup("test");
            fail("should throw an Exception");
        } catch (Exception e) { }
        ksession.retract(fact2);

        assertEquals(0, res.size());

        // try to reuse the ksession after the Exception
        FactHandle fact3 = ksession.insert(new Person("Mario", 38));
        ((AgendaImpl)ksession.getAgenda()).activateRuleFlowGroup("test");
        assertEquals(1, res.size());
        ksession.retract(fact3);

        ksession.dispose();
    }

    @Test
    public void testBigDecimalComparison() throws Exception {
        // JBRULES-3715
        String str = "import org.drools.Person;\n" +
                "rule \"Big Decimal Comparison\"\n" +
                "    dialect \"mvel\"\n" +
                "when\n" +
                "    Person( bigDecimal == 0.0B )\n" +
                "then\n" +
                "end";

        KnowledgeBase kbase = loadKnowledgeBaseFromString(str);
        final StatefulKnowledgeSession ksession = kbase.newStatefulKnowledgeSession();

        Person p = new Person("Mario", 38);
        p.setBigDecimal(new BigDecimal("0"));
        ksession.insert(p);

        assertEquals(1, ksession.fireAllRules());
        ksession.dispose();
    }
}