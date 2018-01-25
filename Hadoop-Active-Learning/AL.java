import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Iterator;

import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Mapper.Context;
import org.apache.hadoop.mapreduce.Reducer;

import elm.elm;
import no.uib.cipr.matrix.DenseMatrix;
import no.uib.cipr.matrix.NotConvergedException;

public class AL {
	private static int NUM_CLASS; //���������	
	private static int NUM_SELECT;		//ѡ�������ĸ���
	private static String TRAIN_PATH;
	private static ArrayList<String> arraylist=new ArrayList<String>();
	public static class ALMapper extends Mapper<LongWritable,Text,DoubleWritable,Text>{
		private DenseMatrix trainfile_matrix;
		private int m;
		private int n;
		private elm e;
		private String splitmark = ",";
		URI uri =null;
		Path local_path;
		@SuppressWarnings("deprecation")
		@Override
		protected void setup(Context context) throws IOException,InterruptedException{  // ѵ���ڱ��ؽ��� �����ڱ��ض�ȡtrainPath�µ�����  
			super.setup(context);

			NUM_SELECT=Integer.parseInt(context.getConfiguration().get("select_num"));
			NUM_CLASS=Integer.parseInt(context.getConfiguration().get("class_num"));
			TRAIN_PATH = context.getConfiguration().get("trainPath");
			

			FileSystem fs = FileSystem.get(context.getConfiguration());
			FileStatus[] fileList = fs.listStatus(new Path(TRAIN_PATH));
			BufferedReader in = null;
			FSDataInputStream fsi = null;
			String line = null;

			for(int i=0;i<fileList.length;i++){
				if(!fileList[i].isDir()){
					fsi=fs.open(fileList[i].getPath());
					in=new BufferedReader(new InputStreamReader(fsi,"UTF-8"));
					while((line=in.readLine())!=null){

						arraylist.add(line);
						String[] strs_t=line.split(splitmark);
						n=strs_t.length;
					}
				}
			}
//	        in.close();
//	        fsi.close();
	        
			m=arraylist.size();

			trainfile_matrix=new DenseMatrix(m,n);

			
			//�����ݹ����trainfile_matrix����
			for(int mm=0;mm<arraylist.size();mm++){
				String str_t=arraylist.get(mm);
				String[] str_t_s=str_t.split(splitmark);
				for(int nn=0;nn<str_t_s.length;nn++){
					trainfile_matrix.set(mm, nn, Double.parseDouble(str_t_s[nn]));
				}
			}
			
			System.out.println(trainfile_matrix.numRows());
			//����ELM
		    e=new elm(1,20,"sig");
			try {
				e.train(trainfile_matrix, NUM_CLASS);
			} catch (NotConvergedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			
			System.out.print(e.getTrainingAccuracy());
			System.out.println();
//			elm e=new elm(1,20,"sig");
//			try {
//				e.train(context.getConfiguration().get("trainPath")+"/tt");
//			} catch (NotConvergedException e1) {
//				// TODO Auto-generated catch block
//				e1.printStackTrace();
//			}
//			System.out.println(e.getTrainingTime());
	}
	
		
		public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException{  // ������Ǵ�������Ĳ��Լ� 
			String line=value.toString();
			String[] words=line.split(splitmark);
			DenseMatrix test_matrix=new DenseMatrix(1,words.length);  // ÿһ�й���һ������
			for(int i=0;i<words.length;i++){  	//���һ��Ϊ���
				test_matrix.set(0, i, Double.parseDouble(words[i]));
			}   
			
			//Ԥ�����
			e.test(test_matrix);
			//�õ�������� һ��numclass�еľ���  
			DenseMatrix d=e.getOutMatrix();
			
			//�����
			double sum= 0;
			for(int i=0;i<d.numColumns();i++){
				sum +=Math.exp(d.get(0, i));
			}
			for(int i=0;i<d.numColumns();i++){
				d.set(0, i, Math.exp(d.get(0, i))/sum);
			}
			
			
//			for(int i=0;i<d.numRows();i++){
//				for(int j=0;j<d.numColumns();j++){
//					System.out.print(d.get(i, j)+"  ");
//				}
//				System.out.println();
//			}
			//������ ��Ϣ��
			double H=0;
			for(int j=0;j<d.numColumns();j++){
				H+=Math.log(d.get(0, j))/Math.log(2.0)*d.get(0, j);
			}
//			System.out.println(H+"   "+value.toString());
			//  ���صĵ���ΪK  ����������ֵΪV ����context ���� ��ΪhadoopĬ�ϵ��ǽ������� ��������Ҫ����ȡǰ����
			context.write(new DoubleWritable(-1/H),new Text(value ));
		}
	}
	public static class ALReduce extends Reducer<DoubleWritable,Text,NullWritable,Text>{  // ȡ��ǰK������Ȼ��ŵ�ѵ������
		public void reduce(DoubleWritable key,Iterable<Text> values,Context context) throws IOException, InterruptedException{
			//��context�Ĵ���˳��ȡǰnum_select������
			if(NUM_SELECT-->0){
				for(Text text:values){
					System.out.println(key.toString()+" "+text.toString());
					context.write(NullWritable.get(), new Text(text));
				}
			}
		}
	}
}
