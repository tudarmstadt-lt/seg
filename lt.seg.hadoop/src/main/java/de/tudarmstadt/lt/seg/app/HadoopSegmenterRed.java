package de.tudarmstadt.lt.seg.app;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Iterator;
import java.util.regex.Pattern;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.OptionBuilder;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.FileInputFormat;
import org.apache.hadoop.mapred.FileOutputFormat;
import org.apache.hadoop.mapred.JobClient;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.MapReduceBase;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Reducer;
import org.apache.hadoop.mapred.Reporter;
import org.apache.hadoop.mapred.TextInputFormat;
import org.apache.hadoop.mapred.TextOutputFormat;
import org.apache.hadoop.mapred.lib.IdentityMapper;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.mortbay.log.Log;

import de.tudarmstadt.lt.seg.sentence.ISentenceSplitter;
import de.tudarmstadt.lt.seg.token.ITokenizer;
import de.tudarmstadt.lt.utilities.cli.ExtendedGnuParser;

/**
 * 
 * hadoop jar lt.seg.hadoop-0.7.0-SNAPSHOT-jar-with-dependencies.jar de.tudarmstadt.lt.seg.app.HadoopSegmenterRed -Dmapreduce.job.queuename=shortrunning -Dmapreduce.job.reduces=10000 --key-column 0 --text-column 2 -f ${in} -o ${out}
 * 
 * @author rem
 *
 */
@SuppressWarnings("static-access")
public class HadoopSegmenterRed extends Configured implements Tool {

	static {
		Segmenter.opts.addOption(OptionBuilder.withLongOpt("keycolumn").withArgName("column-of-document-key").hasArg().withDescription("Specify the column that contains the document key starting from 0. Specify '-1' to use line id. (default: '-1').").create("ck"));
		Segmenter.opts.addOption(OptionBuilder.withLongOpt("textcolumn").withArgName("column-of-document-text").hasArg().withDescription("Specify the column that contains the document starting from 0. Specify '-1' to use whole line. (default: '-1').").create("ct"));
	}

	public static void main(String[] args) throws Exception {
		// try to instantiate a segmenter, tokenizer, sentencesplitter. If something doesn't work application is killed already here instead of the mapper
		Segmenter segmenter = new Segmenter(args);
		segmenter.newSentenceSplitter();
		segmenter.newTokenizer();

		int res = ToolRunner.run(new Configuration(),new HadoopSegmenterRed(), args);
		System.exit(res);
	}

	public int run(String[] args) throws Exception {

		System.out.println(Arrays.toString(args));

		JobConf conf = new JobConf(getConf(), HadoopSegmenterRed.class);
		conf.setJobName(HadoopSegmenterRed.class.getSimpleName());
		//		conf.setQueueName("shortrunning");

		System.out.println("queuename: " + conf.getQueueName());

		conf.setMapperClass(IdentityMapper.class);
		conf.setReducerClass(SegmentationReducer.class);

		conf.setInputFormat(TextInputFormat.class);
		conf.setOutputFormat(TextOutputFormat.class);

		conf.setMapOutputKeyClass(LongWritable.class);
		conf.setMapOutputValueClass(Text.class);
		
		conf.setOutputKeyClass(NullWritable.class);
		conf.setOutputValueClass(Text.class);

		conf.setStrings("cli-args", args);
		
		// instantiate a segmenter to get the options right
		Segmenter segmenter = new Segmenter(args);
		FileInputFormat.setInputPaths(conf, new Path(segmenter._filename_in));
		FileOutputFormat.setOutputPath(conf, new Path(segmenter._filename_out));

		// delete output path (for testing purposes)
		// FileSystem.get(conf).delete(new Path(args[1]), true);

		JobClient.runJob(conf);
		return 0;
	}

	public static class SegmentationReducer extends MapReduceBase implements Reducer<LongWritable, Text, NullWritable, Text> {
		
		static {
			Segmenter.opts.addOption(OptionBuilder.withLongOpt("keycolumn").withArgName("column-of-document-key").hasArg().withDescription("Specify the column that contains the document key starting from 0. Specify '-1' to use line id. (default: '-1').").create("ck"));
			Segmenter.opts.addOption(OptionBuilder.withLongOpt("textcolumn").withArgName("column-of-document-text").hasArg().withDescription("Specify the column that contains the document starting from 0. Specify '-1' to use whole line. (default: '-1').").create("ct"));
		}

		Segmenter _segmenter;
		ISentenceSplitter _sentenceSplitter;
		ITokenizer _tokenizer;

		int _col_key = -1;
		int _col_text = -1;

		@Override
		public void configure(JobConf job) {
			String[] args = job.getStrings("cli-args");
			System.out.println(Arrays.toString(args));
			
			_segmenter = new Segmenter(args);
			try{
				_sentenceSplitter = _segmenter.newSentenceSplitter();
				_tokenizer = _segmenter.newTokenizer();

				CommandLine cmd = new ExtendedGnuParser(true).parse(Segmenter.opts, args);
				_col_key = Integer.parseInt(cmd.getOptionValue("keycolumn", "-1" ));
				_col_text = Integer.parseInt(cmd.getOptionValue("textcolumn", "-1" ));

			}catch(Exception e){
				throw new RuntimeException(e);
			}

			super.configure(job);
		}


		@Override
		public void reduce(LongWritable key, Iterator<Text> values, OutputCollector<NullWritable, Text> output, Reporter reporter) throws IOException {
			reporter.progress();
			
			String docid = String.valueOf(key.get());
			
			while(values.hasNext()){
				String line = values.next().toString();
				
				if(_col_text > -1 || _col_key > -1){
					String[] columns = line.split(Pattern.quote("\t"));
					if(_col_key > -1){
						if(columns.length <= _col_key){
							Log.warn(String.format("Key column does not exist for line '%s': %d, columns: %d.", docid, _col_key, columns.length));
							continue;
						}
						docid = columns[_col_key];
					}
					if(_col_text > -1){
						if(columns.length <= _col_text){
							Log.warn(String.format("Text column does not exist for key='%s': %d, columns: %d.", docid, _col_key, columns.length));
							continue;
						}
						line = columns[_col_text];
					}
				}

				Reader reader = new StringReader(line.replace("\\t", "\t").replace("\\n", "\n"));
				StringWriter sw = new StringWriter();
				PrintWriter writer = new PrintWriter(sw);

				Segmenter.split_and_tokenize(
						reader, 
						docid, 
						_sentenceSplitter, 
						_tokenizer, 
						_segmenter._level_filter,
						_segmenter._level_normalize,
						_segmenter._merge_types, 
						_segmenter._merge_tokens, 
						"\n",// _segmenter._separator_sentence, 
						_segmenter._separator_token, 
						"\t",//_segmenter._separator_desc, 
						writer);

				Text out_text = new Text();
				for(String sentence_line : sw.toString().split("\n")){
					out_text.set(sentence_line);
					output.collect(NullWritable.get(), out_text);
				}
			}
		}

	}

}
