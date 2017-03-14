package de.tudarmstadt.lt.seg.app;

import java.io.File;
import java.io.IOException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.util.ToolRunner;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class HadoopSegmenterTest {

	static File _temp_folder;

	@BeforeClass
	public static void setupTest() throws IOException {
		TemporaryFolder f = new TemporaryFolder();
		f.create();
		_temp_folder = f.getRoot();
		System.out.println("created temporary folder: " + _temp_folder.getAbsolutePath());
	}

	@Test
	public void test_preparsed() throws Exception {
		String in = "hadoop-docs-test";
		String out = "hadoop-out-segmenter";

		File out_dir = new File(_temp_folder, out);
		ToolRunner.run(new Configuration(), new HadoopSegmenter(), new String[] {
			"-Dmapreduce.job.queuename=test",
			"-Dmapred.job.queue.name=test2",
			"--file", ClassLoader.getSystemClassLoader().getResource(in).getPath(),
			"--out", out_dir.getAbsolutePath(),
			"--textcolumn", "1",
			"--keycolumn", "0"
		});
		Assert.assertTrue("Output directory does not exist.", out_dir.exists());
		Assert.assertTrue("Hadoop processing was not successful.", new File(out_dir, "_SUCCESS").exists());
		System.out.format("Outputfiles can be found in '%s'.", out_dir);
		
	}
	
	@Test
	@Ignore
	public void testMain() throws Exception{
		String in = "hadoop-docs-test";
		String out = "hadoop-out-segmenter2";

		File out_dir = new File(_temp_folder, out);
		HadoopSegmenter.main(new String[]{
			"-Dmapreduce.job.queuename=test",
			"-Dmapred.job.queue.name=test2",
			"--file", ClassLoader.getSystemClassLoader().getResource(in).getPath(),
			"--out", out_dir.getAbsolutePath(),
			"--textcolumn", "1",
			"--keycolumn", "0"
		});
		Assert.assertTrue("Output directory does not exist.", out_dir.exists());
		Assert.assertTrue("Hadoop processing was not successful.", new File(out_dir, "_SUCCESS").exists());
		System.out.format("Outputfiles can be found in '%s'.", out_dir);
	}
}
