/*
 * Copyright (C) 2013 salesforce.com, inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.auraframework.util.test.diff;

import org.auraframework.util.IOUtil;
import org.auraframework.util.adapter.SourceControlAdapter;
import org.auraframework.util.test.util.UnitTestCase;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.net.URL;

public abstract class BaseDiffUtils<T> implements DiffUtils<T> {

    protected final UnitTestCase test;
    private URL srcUrl;
    private URL destUrl;

    public BaseDiffUtils(UnitTestCase test, String goldName) throws Exception {
        this.test = test;

        Class<? extends UnitTestCase> testClass = test.getClass();
        String relativeResourceName = testClass.getSimpleName() + (goldName.startsWith("/") ? "" : "/")
                + goldName;
              
        String explicitResultsFolder = test.getExplicitGoldResultsFolder();
        if (explicitResultsFolder != null) {
            srcUrl = destUrl = new URL("file://" + explicitResultsFolder + '/' + goldName);       	
            return;
        }

        // auto-detect gold file location logic:
        String resourceName = getResultsFolder() + relativeResourceName;
        srcUrl = testClass.getResource(resourceName);
        if (srcUrl == null) {
            // gold file not found, but try to identify expected gold file location based on the test class location
            String relPath = testClass.getName().replace('.', '/') + ".class";
            URL testUrl = testClass.getResource("/" + relPath);
            if ("file".equals(testUrl.getProtocol())) {
                String fullPath = testUrl.getPath();
                String basePath = fullPath.substring(0, fullPath.indexOf(relPath)).replaceFirst(
                        "/target/test-classes/", "/src/test");
                destUrl = new URL("file://" + basePath + resourceName);
            }
        } else if ("file".equals(srcUrl.getProtocol())) {
            // probably in dev so look for source rather than target
            String devPath = srcUrl.getPath().replaceFirst("/target/test-classes/", "/src/test/");
            srcUrl = destUrl = new URL("file://" + devPath);
        }

        if (destUrl == null) {
            // if we're reading from jars and can't identify filesystem source locations, write to a temp file at least
            destUrl = new URL("file://" + IOUtil.getDefaultTempDir() + resourceName);
        }
        if (srcUrl == null) {
            // also if reading from jars and no gold included (shouldn't happen)
            srcUrl = destUrl;
        }
    }

    @Override
    public final UnitTestCase getTest() {
        return test;
    }

    /**
     * Override to change it
     */
    protected String getResultsFolder() {
        return "/results/";
    }

    @Override
    public URL getUrl() {
        return srcUrl;
    }

    protected URL getDestUrl() {
        return destUrl;
    }

    /**
     * try to invoke "diff" to create a readable diff for the test failure results, otherwise append our crappy
     * unreadable garbage
     */
    protected void appendDiffs(String results, String gold, StringBuilder sb) {
        try {
            // create a temp file and write the results so that we're sure to
            // have something for diff to use
            File resultsFile = File.createTempFile("aura-results.", ".xml");
            File goldFile = File.createTempFile("aura-gold.", ".xml");
            try {
                OutputStreamWriter fw1 = new OutputStreamWriter(new FileOutputStream(resultsFile), "UTF-8");
                OutputStreamWriter fw2 = new OutputStreamWriter(new FileOutputStream(goldFile), "UTF-8");
                try {
                    fw1.write(results);
                    fw2.write(gold);
                } finally {
                    fw1.close();
                    fw2.close();
                }
                Process child = Runtime.getRuntime().exec("diff -du " + goldFile.getPath() + " " + resultsFile.getPath());
                try {
                    printToBuffer(sb, child.getInputStream());
                    printToBuffer(sb, child.getErrorStream());
                } finally {
                    child.waitFor();
                }
            } finally {
                resultsFile.delete();
                goldFile.delete();
            }
        } catch (Throwable t) {
        }
    }

    private boolean printToBuffer(StringBuilder sb, InputStream in) throws IOException {
        boolean printedAny = false;
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        try {
            String line = reader.readLine();
            while (null != line) {
                sb.append(line).append("\n");
                printedAny = true;
                line = reader.readLine();
            }
            return printedAny;
        } finally {
            reader.close();
        }
    }

    protected final void writeGoldFileContent(String content) {
        URL url = getDestUrl();
        SourceControlAdapter sca = this.test.getSourceControlAdapter();
        try {
            File f = new File(url.getFile());
            boolean existed = f.exists();
            if (existed && !f.canWrite() && sca.canCheckout()) {
                sca.checkout(f);
            }
            if (!f.getParentFile().exists()) {
                f.getParentFile().mkdirs();
            }
            OutputStreamWriter fw = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
            fw.write(content);
            fw.close();

            if (!existed && sca.canCheckout()) {
                sca.add(f);
            }
        } catch (Throwable t) {
            throw new RuntimeException("Failed to write gold file: " + url.toString(), t);
        }
    }

    protected final String readGoldFileContent() throws IOException {
        final int READ_BUFFER = 4096;

        Reader br = new BufferedReader(new InputStreamReader(getUrl().openStream(), "UTF-8"));
        char[] buff = new char[READ_BUFFER];
        int read = -1;
        StringBuffer sb = new StringBuffer(READ_BUFFER);
        while ((read = br.read(buff, 0, READ_BUFFER)) != -1) {
            sb.append(buff, 0, read);
        }
        br.close();
        return sb.toString();
    }
}
