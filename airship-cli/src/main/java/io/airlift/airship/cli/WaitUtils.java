package com.proofpoint.galaxy.cli;

public final class WaitUtils
{
    private WaitUtils()
    {
    }

    public static void wait(int loop)
    {
        switch (loop % 5) {
            case 0:
                clearWaitMessage();
                break;
            case 1:
                System.out.print("\r1.");
                break;
            case 2:
                System.out.print(" 2.");
                break;
            case 3:
                System.out.print(" 3.");
                break;
            case 4:
                System.out.print("  GO!");
                break;
        }
        try {
            Thread.sleep(500);
        }
        catch (InterruptedException e) {
        }
    }

    public static void clearWaitMessage()
    {
        System.out.print("\r                            \r");
    }
}
