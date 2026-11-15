package practice.prep.problems;

public class AddDecimals {
    public String add(String x, String y) {
        int xDecimalPlaces = x.indexOf('.') == -1 ? 0 : x.length() - x.indexOf('.') - 1;
        int yDecimalPlaces = y.indexOf('.') == -1 ? 0 : y.length() - y.indexOf('.') - 1;
        x = ensureDecimalPlaces(x, xDecimalPlaces, yDecimalPlaces);
        y = ensureDecimalPlaces(y, yDecimalPlaces, xDecimalPlaces);

        StringBuilder res = new StringBuilder();
        int carry = 0;
        int i = x.length() - 1;
        int j = y.length() - 1;
        while (i >= 0 || j >= 0 || carry > 0) {
            if (i >= 0 && x.charAt(i) == '.') {
                res.append('.');
                i--;
                j--;
                continue;
            }

            int digit = carry;
            if (i >= 0) {
                digit += (int)(x.charAt(i) - '0');
                i--;
            }

            if (j >= 0) {
                digit += (int)(y.charAt(j) - '0');
                j--;
            }

            res.append(digit % 10);
            carry = digit / 10;
        }

        return res.reverse().toString();
    }

    private String ensureDecimalPlaces(String n, int thisCount, int thatCount) {
        if (thisCount >= thatCount) {
            return n;
        }

        StringBuilder padded = new StringBuilder();
        padded.append(n);
        if (thisCount == 0) {
            padded.append('.');
        }

        // (this, 2) 123.45 | (that, 4) 2.4532
        padded.append("0".repeat(thatCount - thisCount));
        return padded.toString();
    }

    public String add_try1(String x, String y) {
        int xDecimalCount = x.indexOf('.') == -1 ? 0 : x.length() - x.indexOf('.') - 1;
        int yDecimalCount = y.indexOf('.') == -1 ? 0 : y.length() - y.indexOf('.') - 1;
        if (xDecimalCount > 0 || yDecimalCount > 0) {
            // make sure both x and y have same number of digits after decimal
            if (xDecimalCount > yDecimalCount) {
                if (yDecimalCount == 0) {
                    y = y + ".";
                }

                y = y + "0".repeat(xDecimalCount - yDecimalCount);
            } else if (yDecimalCount > xDecimalCount) {
                if (xDecimalCount == 0) {
                    x = x + ".";
                }

                x = x + "0".repeat(yDecimalCount - xDecimalCount);
            }
        }

        int carry = 0;
        int i = x.length() - 1;
        int j = y.length() - 1;
        StringBuilder result = new StringBuilder();
        while (i >= 0 && j >= 0) {
            if (x.charAt(i) == '.') {
                result.append('.');
                i--;
                j--;
                continue;
            }

            int xD = (int)(x.charAt(i--) - '0');
            int yD = (int)(y.charAt(j--) - '0');
            int rD = xD + yD + carry;
            carry = rD/10;
            rD = rD%10;
            result.append(rD);
        }

        while (i >= 0) {
            int xD = (int)(x.charAt(i--) - '0');
            int rD = xD + carry;
            carry = rD/10;
            rD = rD%10;
            result.append(rD);
        }

        while (j >= 0) {
            int yD = (int)(y.charAt(j--) - '0');
            int rD = yD + carry;
            carry = rD/10;
            rD = rD%10;
            result.append(rD);
        }

        if (carry > 0) {
            result.append(carry);
        }

        return result.reverse().toString();
    }
}
