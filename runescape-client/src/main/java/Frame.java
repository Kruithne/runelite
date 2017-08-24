import net.runelite.mapping.Export;
import net.runelite.mapping.Implements;
import net.runelite.mapping.ObfuscatedName;
import net.runelite.mapping.ObfuscatedSignature;

@ObfuscatedName("ex")
@Implements("Frame")
public class Frame {
   @ObfuscatedName("i")
   static int[] field1906;
   @ObfuscatedName("j")
   static int[] field1902;
   @ObfuscatedName("a")
   static int[] field1899;
   @ObfuscatedName("r")
   static int[] field1900;
   @ObfuscatedName("d")
   @Export("showing")
   boolean showing;
   @ObfuscatedName("o")
   @ObfuscatedSignature(
      signature = "Lei;"
   )
   @Export("skin")
   FrameMap skin;
   @ObfuscatedName("n")
   int field1907;
   @ObfuscatedName("q")
   int[] field1903;
   @ObfuscatedName("b")
   @Export("translator_x")
   int[] translator_x;
   @ObfuscatedName("k")
   @Export("translator_y")
   int[] translator_y;
   @ObfuscatedName("s")
   @Export("translator_z")
   int[] translator_z;

   static {
      field1906 = new int[500];
      field1902 = new int[500];
      field1899 = new int[500];
      field1900 = new int[500];
   }

   @ObfuscatedSignature(
      signature = "([BLei;)V"
   )
   Frame(byte[] var1, FrameMap var2) {
      this.skin = null;
      this.field1907 = -1;
      this.showing = false;
      this.skin = var2;
      Buffer var3 = new Buffer(var1);
      Buffer var4 = new Buffer(var1);
      var3.offset = 2;
      int var5 = var3.readUnsignedByte();
      int var6 = -1;
      int var7 = 0;
      var4.offset = var5 + var3.offset;

      int var8;
      for(var8 = 0; var8 < var5; ++var8) {
         int var9 = var3.readUnsignedByte();
         if(var9 > 0) {
            if(this.skin.types[var8] != 0) {
               for(int var10 = var8 - 1; var10 > var6; --var10) {
                  if(this.skin.types[var10] == 0) {
                     field1906[var7] = var10;
                     field1902[var7] = 0;
                     field1899[var7] = 0;
                     field1900[var7] = 0;
                     ++var7;
                     break;
                  }
               }
            }

            field1906[var7] = var8;
            short var11 = 0;
            if(this.skin.types[var8] == 3) {
               var11 = 128;
            }

            if((var9 & 1) != 0) {
               field1902[var7] = var4.readShortSmart();
            } else {
               field1902[var7] = var11;
            }

            if((var9 & 2) != 0) {
               field1899[var7] = var4.readShortSmart();
            } else {
               field1899[var7] = var11;
            }

            if((var9 & 4) != 0) {
               field1900[var7] = var4.readShortSmart();
            } else {
               field1900[var7] = var11;
            }

            var6 = var8;
            ++var7;
            if(this.skin.types[var8] == 5) {
               this.showing = true;
            }
         }
      }

      if(var1.length != var4.offset) {
         throw new RuntimeException();
      } else {
         this.field1907 = var7;
         this.field1903 = new int[var7];
         this.translator_x = new int[var7];
         this.translator_y = new int[var7];
         this.translator_z = new int[var7];

         for(var8 = 0; var8 < var7; ++var8) {
            this.field1903[var8] = field1906[var8];
            this.translator_x[var8] = field1902[var8];
            this.translator_y[var8] = field1899[var8];
            this.translator_z[var8] = field1900[var8];
         }

      }
   }
}