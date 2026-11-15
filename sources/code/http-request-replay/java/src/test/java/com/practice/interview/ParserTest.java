package com.practice.interview;

import java.util.List;
import org.junit.Test;

public class ParserTest {
    private static final String TEST_JSON = """
[
  {
    "request": {
      "url": "/v1/charges",
      "headers": {
        "Host": "api.example.com",
        "Content-Type": "application/x-www-form-urlencoded"
      },
      "body": "amount=123&currency=usd&card%5Bnumber%5D=example&card%5Bexp_month%5D=12&card%5Bexp_year%5D=2070",
      "method": "POST"
    },
    "response": {
      "body": "{\\"id\\": \\"ch_example1\\", \\"currency\\": \\"usd\\", \\"object\\": \\"charge\\", \\"status\\": \\"succeeded\\"}",
      "headers": {
        "Content-Type": "application/json"
      },
      "code": 200
    }
  },
  {
    "request": {
      "url": "/v1/charges",
      "headers": {
        "Host": "api.example.com",
        "Content-Type": "application/x-www-form-urlencoded"
      },
      "body": "amount=123&currency=usd&card%5Bnumber%5D=example&card%5Bexp_month%5D=12&card%5Bexp_year%5D=2070&card%5Baddress_line1%5D=example&card%5Baddress_zip%5D=example",
      "method": "POST"
    },
    "response": {
      "body": "{\\"id\\": \\"ch_example2\\", \\"currency\\": \\"usd\\", \\"object\\": \\"charge\\", \\"status\\": \\"succeeded\\"}",
      "headers": {
        "Content-Type": "application/json"
      },
      "code": 200
    }
  }
]
""";

    @Test
    public void validJson() {
        Parser parser = new Parser();
        List<MyLog> logList = parser.parseContent(TEST_JSON);
        System.out.println(logList.size());
    }

}
