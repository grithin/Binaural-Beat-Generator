import java.nio.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;


class BinauralBeats {
	/**
	@param	duration	seconds
	@param	volume	0 to 1
	*/
	public static void makeWave(BufferedOutputStream writer, int sampleRate, float frequency, float duration, float volume){
		/**
		a frequency represents how many times the whole sine wave cycles per second
		the number of sine waves can be represented as revolutions around a circle
		a revolution around a circle is represented by radians as 2pi
		a frequency represented in radians is frequency * 2pi
		the position of the sine wave at any timeSpot can be found by sin(frequency * 2pi * timeSpot)
		
		here, we can convert timeSpot from samples to seconds by dividing by sample rate
		and so, the position of the sine wave at any sample becomes
		sin(frequency * 2pi * (sampleOffset/sampleRate))
		
		to reduce this computation when applying aa loop, you look at what variables are avaiable outside of the loop
		we see frequency,, 2pi, and sampleRate are available
		so, we pre compute this to 
			preComputed = (2pi * frequency/sampleRate)
		and apply in loop
			sin(preComputed * sampleOffset)
		*/
		float preComputed = 2 * (float)Math.PI * frequency / sampleRate;
		///the for(initialise; test; onLoop) format
		try{
			for(int samples = Math.round(sampleRate * duration), sampleOffset = 0; sampleOffset < samples; sampleOffset++){
				float sineWayPosition = (float)Math.sin(preComputed * sampleOffset);
				///change the type conversion according to encoding.   Byte for type 2 (8 bit), char for type 3 (16bit),
				writer.write(BinauralBeats.toBytes((char) (Math.round(127 * volume * sineWayPosition))));
			}
		}catch(IOException x) {
			System.err.format("IOException: %s%n", x);
		}
	}
	public static void makeFrequencySequenceFile(float[][] frequencySet, int sampleRate, float totalDuration, String fileName){
		/**
		https://en.wikipedia.org/wiki/Au_file_format
		.au files have all datta in big endian
		.au files have 6 x 32bit words for header
		*/
		/**
		a note on java bytes
			Byte is a signed datatype in Java. It can take values from -128 (-0x80) to 127 (0x7f). Some of your constants won't fit in this range and are not, therefore, valid byte's. That's unlike C++, where BYTE is usually defined to be unsigned char. 
		*/
		///6 * 32bit words  / 8bits(per byte)
		ByteBuffer buffer = ByteBuffer.allocate(32 * 6 / 8); 
		///java stores everything in BIG_ENDIAN regardless, but this is left in for learning
		buffer.order(ByteOrder.BIG_ENDIAN);
		
		///this is a try-with-resoruce format.  It will close the resource automatically.  Otherwise, use writer.flush(); writer.close();
		///for normal file, written as  try (BufferedWriter writer = Files.newBufferedWriter('side1.au', Charset.forName("US-ASCII"))) {
		try{
			BufferedOutputStream writer = new BufferedOutputStream(new FileOutputStream(fileName));
		
			//first 32 word: the value 0x 2e 73 6e 64 (four ASCII characters ".snd")
			buffer.put((byte)0x2e);
			buffer.put((byte)0x73);
			buffer.put((byte)0x6e);
			buffer.put((byte)0x64);
			//data offset in 32-bit words (header size)
			buffer.putInt(24);
			//data size.  Since we are using encoding sample size (in bytes) * duration * sampleRate
			buffer.putInt(Math.round(sampleRate * totalDuration * 2));
			//encoding (ints in java are 32 bit)
			buffer.putInt(3);
			//sample rate
			buffer.putInt(sampleRate);
			//channels
			buffer.putInt(1);
			try{
				writer.write(buffer.array());
				for(int i = 0; i < frequencySet.length; i++){
					BinauralBeats.makeWave(writer, sampleRate, frequencySet[i][0], frequencySet[i][1], (float)0.8);
				}
			}catch(IOException x) {
				System.err.format("IOException: %s%n", x);
			}finally{
				writer.close();
			}
		}catch(FileNotFoundException x){
			System.err.format("FileNotFoundException: %s%n", x);
		}catch(IOException x) {
			System.err.format("FileNotFoundException: %s%n", x);
		}
	}
	/**
	@param	variations	[][frequency, durationSeconds]
	*/
	public static void makeBinaural(int centerFrequency, float[][] variations, String fileName){
		//array of arrays of floats
		float[][] frequencySet1 = new float[variations.length][2];
		float[][] frequencySet2 = new float[variations.length][2];
		
		float totalDuration = 0;
		
		for(int i = 0; i < variations.length; i++){
			float[] setItem1 = {(float)centerFrequency + variations[i][0]/(float)2.0, variations[i][1]};
			frequencySet1[i] = setItem1;
			float[] setItem2 = {centerFrequency - variations[i][0]/(float)2.0, variations[i][1]};
			frequencySet2[i] = setItem2;
			totalDuration += variations[i][1];
		}
		
		BinauralBeats.makeFrequencySequenceFile(frequencySet1, 16000, totalDuration, "side1.au");
		BinauralBeats.makeFrequencySequenceFile(frequencySet2, 16000, totalDuration, "side2.au");
		BinauralBeats.combineSides(fileName, "side1.au", "side2.au", 16000, totalDuration);
	}
	
	public static void combineSides(String fileName, String side1, String side2, int sampleRate, float totalDuration){
		try{
			byte[] side1Bytes = Files.readAllBytes(Paths.get(side1));
			byte[] side2Bytes = Files.readAllBytes(Paths.get(side2));
			try{
				BufferedOutputStream writer = new BufferedOutputStream(new FileOutputStream(fileName));
				try{
					ByteBuffer buffer = ByteBuffer.allocate(32 * 6 / 8); 
					buffer.put(BinauralBeats.hexStringToByteArray("2e736e64"));
					//can't use    writer.write b/c that only writes bytes, and no apparent way better than ByteBuffer to convert int to bytes
					buffer.putInt(24);
					buffer.putInt(Math.round(sampleRate * totalDuration * 2 * 2));
					buffer.putInt(3);
					buffer.putInt(sampleRate);
					buffer.putInt(2);
					
					writer.write(buffer.array());
					//the sides are packed in 2's, there should be no out of bounds
					for(int i = 24; i < side1Bytes.length; i+=2){
						writer.write(side1Bytes[i]);
						writer.write(side1Bytes[i+1]);
						writer.write(side2Bytes[i]);
						writer.write(side2Bytes[i+1]);
					}
				}catch(IOException x) {
					System.err.format("IOException: %s%n", x);
				}finally{
					writer.close();
				}
			}catch(FileNotFoundException x){
				System.err.format("FileNotFoundException: %s%n", x);
			}catch(IOException x) {
				System.err.format("FileNotFoundException: %s%n", x);
			}
		}catch(IOException x) {
			System.err.format("FileNotFoundException: %s%n", x);
		}
	}
	public static byte[] toBytes(int x){
		return ByteBuffer.allocate(4).putInt(x).array();
	}
	public static byte[] toBytes(char x){
		return ByteBuffer.allocate(2).putChar(x).array();
	}
	public static byte[] hexStringToByteArray(String s) {
		int len = s.length();
		//s.length() represents the number of characters (not the number of bytes).  Each character in hex code represents 2^4 so (half a byte).  So, do /2
		byte[] data = new byte[len / 2];
		//read 2 characters to get single byte
		for (int i = 0; i < len; i += 2) {
			data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)//convert first character to digit, then move its bits 4 to the right, to make room for  the next hex character
								 + Character.digit(s.charAt(i+1), 16));
		}
	return data;
}
	
	
	public static void main(String[] args) {
		// format:  {{binaural frequency, duration in seconts},...}
		float[][] variations = {{2,10},{4,10},{8,10},{16,10},{32,10},{40,10},{44,10},{46,10}};
		BinauralBeats.makeBinaural(2112, variations, "test.au");
	}
}