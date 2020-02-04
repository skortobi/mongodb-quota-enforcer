$(document).ready(function () {
        $("#myTable").tablesorter({
          sortList: [[1, 1]],
          headers: {
            // assign the secound column (we start counting zero)
            0: {
              // disable it by setting the property sorter to false
              sorter: false
            }
          }
        });
      });